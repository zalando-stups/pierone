(ns pierone.core
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-body]]
            [ring.util.response :refer :all]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [cheshire.core :as json]
            [pierone.backend.file :refer :all])
  (:import
    [java.util Arrays]
    (java.io ByteArrayInputStream)))

(defn as-json [resp]
  "Set response Content-Type to application/json"
  (content-type resp "application/json"))

(defn json-response [data]
  (-> (response data)
      as-json))

(defn check-v2 [request]
  (-> (json-response "Registry API v2 is not implemented")
      (status 404)))

(defn index [request]
  (-> (response "<h1>Welcome to Pier One!<h1>
                 <a href='/ui/'>Swagger UI</a>")
      (content-type "text/html")))

(defn ping [request]
  (-> (json-response true)
      (header "X-Docker-Registry-Version" "0.6.3")))

(defn put-repo [request]
  (-> (json-response "OK")
      (header "X-Docker-Token" "FakeToken")
      (header "X-Docker-Endpoints" (get-in request [:headers "host"]))))

(defn get-image-json-data [image-id]
  (let [data (get-object (str image-id ".json"))]
    (if data
      (json/parse-string (String. data))
      nil)
    ))

(defn get-image-json [request]
  (let [image-id (get-in request [:parameters :path :image])
        data (get-image-json-data image-id)]
    (if data
      (json-response data)
      (-> (json-response "Image not found")
          (status 404)))))


(defn store-image-json [image-id data]
  (log/info "Storing image JSON" image-id)
  (put-object (str image-id ".json") (-> data
                                         json/generate-string
                                         (.getBytes "UTF-8"))))

(defn put-image-json [request]
  (let [image-id (get-in request [:parameters :path :image])]

    (store-image-json image-id (:body request))
    (json-response "OK")))

(defn put-image-layer [request]
  (let [image-id (get-in request [:parameters :path :image])]
    (put-object (str image-id ".layer") (org.apache.commons.io.IOUtils/toByteArray (:body request)))
    (json-response "OK")))

(defn get-image-layer [request]
  (let [image-id (get-in request [:parameters :path :image])
        bytes (get-object (str image-id ".layer"))]
    (if bytes
      (-> (response (ByteArrayInputStream. bytes))
          (content-type "application/octect-stream"))
      (-> (response "Layer not found")
          (status 404)))))

(defn put-image-checksum [request]
  (json-response "OK"))

(defn put-tag [request]
  (let [repo1 (get-in request [:parameters :path :repo1])
        repo2 (get-in request [:parameters :path :repo2])
        tag (get-in request [:parameters :path :tag])
        path (str repo1 "/" repo2 "/tags/" tag ".json")
        obj (get-object path)
        bytes (-> request
                  :body
                  json/generate-string
                  (.getBytes "UTF-8"))
        ]

    (if obj
      (if (Arrays/equals obj bytes)
        (response "OK")
        (-> (json-response "Conflict: tags are immutable")
            (status 409)))
      (do
        (put-object path bytes)
        (json-response "OK")))))

(defn read-tag [path]
  (let [basename (last (.split path "/"))
        tag (.substring basename 0 (- (.length basename) 5))]
    {tag (json/parse-string (String. (get-object path)))}))

(defn get-tags [request]
  (let [repo1 (get-in request [:parameters :path :repo1])
        repo2 (get-in request [:parameters :path :repo2])
        path (str repo1 "/" repo2 "/tags/")
        tag-paths (list-objects path)]
    (if (seq tag-paths)
      (json-response (reduce merge (map read-tag tag-paths)))
      (-> (json-response {})
          (status 404)))))

(defn put-images [request]
  "this is the final call from Docker client when pushing an image
   Docker client expects HTTP status code 204 (No Content) instead of 200 here!"
  (let [repo1 (get-in request [:parameters :path :repo1])
        repo2 (get-in request [:parameters :path :repo2])
        path (str repo1 "/" repo2 "/images.json")
        ]
    ; TODO: the body is actually empty/useless here
    (put-object path (-> request :body json/generate-string (.getBytes "UTF-8")))
    (-> (response "")
        (status 204))))

(defn get-images [request]
  (json-response []))

(defn get-ancestry
  ([image-id]
    (get-ancestry image-id []))
  ([image-id ancestry]
    (let [data (get-image-json-data image-id)
          parent (get data "parent")
          new-ancestry (conj ancestry image-id)]
      (if data
        (if parent
          (get-ancestry parent new-ancestry)
          new-ancestry)
        nil))))

(defn get-image-ancestry [request]
  (let [image-id (get-in request [:parameters :path :image])
        ancestry (get-ancestry image-id)]
    (if ancestry
      (json-response ancestry)
      (-> (json-response "Image not found")
          (status 404)))))

(defn search [request]
  (let [query (get-in request [:parameters :path :q])]
    (json-response {:results []})
    ))

(def app
  (-> (s1st/swagger-executor)
      (s1st/swagger-security)
      (s1st/swagger-validator)
      (s1st/swagger-parser)
      (s1st/swagger-discovery)
      (s1st/swagger-mapper ::s1st/yaml-cp "api.yaml")
      (wrap-json-body)
      (wrap-params)))

