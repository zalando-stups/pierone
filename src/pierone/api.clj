(ns pierone.api
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-body]]
            [ring.util.response :refer :all]
            [ring.adapter.jetty :as jetty]
            [com.stuartsierra.component :as component]
            [org.httpkit.server :as httpkit]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [pierone.backend :as backend])
    (:import
    [java.util Arrays]
    (java.io ByteArrayInputStream)))

(defn new-app [definition backend]
  (let [; our custom mapper that adds the 'backend' as a second parameter to all API functions
        backend-mapper (fn [operationId]
                         (if-let [api-fn (s1st/map-function-name operationId)]
                           (fn [request] (api-fn request backend))))]

        ; the actual ring setup
        (-> (s1st/swagger-executor :mappers [backend-mapper])
                    (s1st/swagger-security)
                    (s1st/swagger-validator)
                    (s1st/swagger-parser)
                    (s1st/swagger-discovery)
                    (s1st/swagger-mapper ::s1st/yaml-cp definition)
                    (wrap-json-body)
                    (wrap-params))
  ))

; 'definition' will be configured during instantiation
; 'httpd' is the internal state of the HTTP server
; 'backend' will be injected via the lifecycle before start
(defrecord API [definition httpd backend]
  component/Lifecycle

  (start [this]
    (if httpd
      (do
        (log/debug "Skipping start of HTTP; already running")
        this)
      (do
        (log/info "Starting HTTP server for API" definition)
        (let [; the actual ring setup
              handler (new-app definition backend)]

          ; use httpkit as ring implementation
          (assoc this :httpd (jetty/run-jetty handler {:port 8080 :join? false}))))))

  (stop [this]
    (if-not httpd
      (do
        (log/debug "Skipping stop of HTTP; not running")
        this)

      (do
        (log/info "Stopping HTTP server")
        (.stop httpd)
        (assoc this :httpd nil)))))

(defn new-api
  "Official constructor for the API."
  [definition]
  (map->API {:definition definition}))


;;; the real business logic! mapped in the api.yaml

(defn image-key [image-id type]
  (str "images/" image-id "." (name type)))

(defn as-json [resp]
  "Set response Content-Type to application/json"
  (content-type resp "application/json"))

(defn json-response [data]
  (-> (response data)
      as-json))

(defn check-v2 [request backend]
  (-> (json-response "Registry API v2 is not implemented")
      (status 404)))

(defn index [request backend]
  (-> (response "<h1>Welcome to Pier One!<h1>
                 <a href='/ui/'>Swagger UI</a>")
      (content-type "text/html")))

(defn ping [request backend]
  (-> (json-response true)
      (header "X-Docker-Registry-Version" "0.6.3")))

(defn put-repo [request backend]
  (-> (json-response "OK")
      (header "X-Docker-Token" "FakeToken")
      (header "X-Docker-Endpoints" (get-in request [:headers "host"]))))

(defn get-image-json-data [backend image-id]
  (let [data (backend/get-object backend (image-key image-id :json))]
    (if data
      (json/parse-string (String. data))
      nil)
    ))

(defn get-image-json [request backend]
  (let [image-id (get-in request [:parameters :path :image])
        data (get-image-json-data backend image-id)]
    (if data
      (json-response data)
      (-> (json-response "Image not found")
          (status 404)))))


(defn store-image-json [backend image-id data]
  (log/info "Storing image JSON" image-id)
  (backend/put-object backend (image-key image-id :json) (-> data
                                         json/generate-string
                                         (.getBytes "UTF-8"))))

(defn put-image-json [request backend]
  (let [image-id (get-in request [:parameters :path :image])]

    (store-image-json backend image-id (:body request))
    (json-response "OK")))

(defn put-image-layer [request backend]
  (let [image-id (get-in request [:parameters :path :image])]
    (log/info image-id (:headers request))
    (backend/put-object backend (image-key image-id :layer) (:body request))
    (json-response "OK")))

(defn get-image-layer [request backend]
  (let [image-id (get-in request [:parameters :path :image])
        bytes (backend/get-object backend (image-key image-id :layer))]
    (if bytes
      (-> (response (ByteArrayInputStream. bytes))
          (content-type "application/octect-stream"))
      (-> (response "Layer not found")
          (status 404)))))

(defn put-image-checksum [request backend]
  (json-response "OK"))

(defn tags-key [repo1 repo2]
  (str "repositories/" repo1 "/" repo2 "/tags/"))

(defn put-tag [request backend]
  (let [repo1 (get-in request [:parameters :path :repo1])
        repo2 (get-in request [:parameters :path :repo2])
        tag (get-in request [:parameters :path :tag])
        path (str (tags-key repo1 repo2) tag ".json")
        obj (backend/get-object backend path)
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
        (backend/put-object backend path bytes)
        (json-response "OK")))))

(defn read-tag [backend path]
  (let [basename (last (.split path "/"))
        tag (.substring basename 0 (- (count basename) 5))]
    {tag (json/parse-string (String. (backend/get-object backend path)))}))

(defn get-tags [request backend]
  (let [repo1 (get-in request [:parameters :path :repo1])
        repo2 (get-in request [:parameters :path :repo2])
        path (tags-key repo1 repo2)
        tag-paths (backend/list-objects backend path)]
    (if (seq tag-paths)
      (json-response (reduce merge (map (partial read-tag backend) tag-paths)))
      (-> (json-response {})
          (status 404)))))

(defn put-images [request backend]
  "this is the final call from Docker client when pushing an image
   Docker client expects HTTP status code 204 (No Content) instead of 200 here!"
    (-> (response "")
        (status 204)))

(defn get-images [request backend]
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

(defn get-image-ancestry [request backend]
  (let [image-id (get-in request [:parameters :path :image])
        ancestry (get-ancestry backend image-id)]
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

(defn search [request backend]
  (let [query (get-in request [:parameters :query :q])
        path "repositories/"
        repo-paths (backend/list-objects backend path)
        repo-names (uniq (map get-repo-name repo-paths))
        filtered-names (filter #(.contains % (or query "")) repo-names)
        results (map (fn [n] {:name n}) filtered-names)]
    (json-response {:results results})
    ))

(defn get-repositories [request backend]
  (let [path "repositories/"
        repo-paths (backend/list-objects backend path)
        repo-names (uniq (map get-repo-name repo-paths))]
    (json-response repo-names)))

