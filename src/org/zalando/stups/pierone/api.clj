(ns org.zalando.stups.pierone.api
  (:require [org.zalando.stups.friboo.system.http :refer [def-http-component]]
            [org.zalando.stups.friboo.log :as log]
            [ring.util.response :refer :all]
            [clojure.data.json :as json]
            [org.zalando.stups.pierone.backend :as backend])
  (:import (java.util Arrays)
           (java.io ByteArrayInputStream)))

; define the API component and its dependencies
(def-http-component API "api/pierone-api.yaml" [backend])

(def default-http-configuration
  {:http-port 8080})

;;; the real business logic! mapped in the pierone-api.yaml

(defn image-key [image-id type]
  (str "images/" image-id "." (name type)))

(defn as-json [resp]
  "Set response Content-Type to application/json"
  (content-type resp "application/json"))

(defn json-response [data]
  (-> (response data)
      as-json))

(defn check-v2 [_ _ _]
  (-> (json-response "Registry API v2 is not implemented")
      (status 404)))

(defn ping [_ _ _]
  (-> (json-response true)
      (header "X-Docker-Registry-Version" "0.6.3")))

(defn put-repo [_ request _]
  (-> (json-response "OK")
      (header "X-Docker-Token" "FakeToken")
      (header "X-Docker-Endpoints" (get-in request [:headers "host"]))))

(defn get-image-json-data [backend image-id]
  (let [data (backend/get-object backend (image-key image-id :json))]
    (if data
      (json/read-str (String. data))
      nil)
    ))

(defn get-image-json [{:keys [image]} _ backend]
  (let [data (get-image-json-data backend image)]
    (if data
      (json-response data)
      (-> (json-response "Image not found")
          (status 404)))))


(defn store-image-json [backend image-id data]
  (log/info "Storing image JSON %s" image-id)
  (backend/put-object backend (image-key image-id :json) data))

(defn put-image-json [{:keys [image]} request backend]
  (store-image-json backend image (:body request))
  (json-response "OK"))

(defn put-image-layer [{:keys [image]} request backend]
  (log/info "image-id: %s headers: %s" image (:headers request))
  (backend/put-object backend (image-key image :layer) (:body request))
  (json-response "OK"))

(defn get-image-layer [{:keys [image]} _ backend]
  (let [bytes (backend/get-object backend (image-key image :layer))]
    (if bytes
      (-> (response (ByteArrayInputStream. bytes))
          (content-type "application/octect-stream"))
      (-> (response "Layer not found")
          (status 404)))))

(defn put-image-checksum [_ _ _]
  (json-response "OK"))

(defn tags-key [repo1 repo2]
  (str "repositories/" repo1 "/" repo2 "/tags/"))

(defn put-tag [{:keys [repo1 repo2 tag]} request backend]
  (let [path (str (tags-key repo1 repo2) tag ".json")
        obj (backend/get-object backend path)
        bytes (:body request)]

    (if obj
      (if (Arrays/equals obj bytes)
        (response "OK")
        (-> (json-response "Conflict: tags are immutable")
            (status 409)))
      (do
        (backend/put-object backend path bytes)
        (json-response "OK")))))

(defn read-tag [backend path]
  (let [basename (last (.split path "/"))
        tag (.substring basename 0 (- (count basename) 5))]
    {tag (json/read-str (String. (backend/get-object backend path)))}))

(defn get-tags [{:keys [repo1 repo2]} _ backend]
  (let [path (tags-key repo1 repo2)
        tag-paths (backend/list-objects backend path)]
    (if (seq tag-paths)
      (json-response (reduce merge (map (partial read-tag backend) tag-paths)))
      (-> (json-response {})
          (status 404)))))

(defn put-images [_ _ _]
  "this is the final call from Docker client when pushing an image
   Docker client expects HTTP status code 204 (No Content) instead of 200 here!"
  (-> (response "")
      (status 204)))

(defn get-images [_ _ _]
  (json-response []))

(defn get-ancestry
  ([backend image-id]
   (get-ancestry backend image-id []))
  ([backend image-id ancestry]
   (let [data (get-image-json-data backend image-id)
         parent (get data "parent")
         new-ancestry (conj ancestry image-id)]
     (if data
       (if parent
         (recur backend parent new-ancestry)
         new-ancestry)
       nil))))

(defn get-image-ancestry [{:keys [image]} _ backend]
  (let [ancestry (get-ancestry backend image)]
    (if ancestry
      (json-response ancestry)
      (-> (json-response "Image not found")
          (status 404)))))

(defn join-path [coll]
  (clojure.string/join "/" coll))

(defn uniq [coll]
  (seq (into #{} coll)))

(defn get-repo-name [path]
  (let [take2 (fn [l] (take 2 l))]
    (-> path
        (.split "/")
        seq
        rest
        take2
        join-path)))

(defn search [{:keys [q]} _ backend]
  (let [path "repositories/"
        repo-paths (backend/list-objects backend path)
        repo-names (uniq (map get-repo-name repo-paths))
        filtered-names (filter #(.contains % (or q "")) repo-names)
        results (map (fn [n] {:name n}) filtered-names)]
    (json-response {:results results})))

(defn get-repositories [_ _ backend]
  (let [path "repositories/"
        repo-paths (backend/list-objects backend path)
        repo-names (uniq (map get-repo-name repo-paths))]
    (json-response repo-names)))

