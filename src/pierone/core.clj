(ns pierone.core
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [cheshire.core :as json])
  (:import
    [java.nio.file.attribute FileAttribute]
    [java.nio.file Files]
    [java.nio.file OpenOption]
    [java.nio.file Paths]))

(def data-path (Paths/get "data" (into-array String [])))

(defn create-dirs [path]
  (Files/createDirectories path (into-array FileAttribute [])))

(defn put-object [key bytes]
  (create-dirs data-path)
  (Files/write (.resolve data-path key) bytes (into-array OpenOption [])))

(defn get-object [key]
  (try
    (Files/readAllBytes (.resolve data-path key))
    (catch java.nio.file.NoSuchFileException e nil)))

(defn check-v2 [request]
  {:status 404
   :body {:message "Registry API v2 is not implemented"}})

(defn index [request]
  {:body {"Welcome" "to Pier One"}})

(defn ping [request]
  {:status 200
   :headers {"Content-Type" "application/json"
             "X-Docker-Registry-Version" "0.6.3"}
   :body "true"})

(defn put-repo [request]
  {:headers {"X-Docker-Token" "FakeToken"
             "X-Docker-Endpoints" (get-in request [:headers "host"])}
   :body {:message "OK"}})

(defn get-image-json [request]
  (let [image-id (get-in request [:parameters :path :image])
        data (get-object (str image-id ".json"))]
    (if data
      {:status 200
       :body (json/parse-string (String. data))}
      {:status 404})))


(defn store-image-json [image-id data]
  (log/info "Storing image JSON" image-id)
  (put-object (str image-id ".json") (-> data
                                         json/generate-string
                                         (.getBytes "UTF-8"))))

(defn put-image-json [request]
  (let [image-id (get-in request [:parameters :path :image])]

    (store-image-json image-id (:body request))
    {:body {:message "OK"}}))

(defn put-image-layer [request]
  (let [image-id (get-in request [:parameters :path :image])]
    (put-object (str image-id ".layer") (org.apache.commons.io.IOUtils/toByteArray (:body request)))
    {:body {:message "OK"}}))

(defn get-image-layer [request]
  (let [image-id (get-in request [:parameters :path :image])]
    {:body (get-object (str image-id ".layer"))}
    )
  )

(defn put-image-checksum [request]
  {:body {:message "OK"}})

(defn put-tag [request]
  {:body {:message "OK"}})

(defn put-images [request]
  {:body {:message "OK"}})

(def app
  (-> (s1st/swagger-executor)
      (s1st/swagger-validator)
      (s1st/swagger-parser)
      (s1st/swagger-mapper ::s1st/yaml-cp "api.yaml")
      (wrap-json-body)
      (wrap-json-response)
      (wrap-params)))
