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
    [java.nio.file Paths]
    [java.util Arrays]))

(def data-path (Paths/get "data" (into-array String [])))

(defn create-dirs [path]
  (Files/createDirectories path (into-array FileAttribute [])))

(defn put-object [key bytes]
  (let [full-path (.resolve data-path key)]
    (create-dirs (.getParent full-path))
    (Files/write full-path bytes (into-array OpenOption []))))

(defn get-object [key]
  (try
    (Files/readAllBytes (.resolve data-path key))
    (catch java.nio.file.NoSuchFileException e nil)))

(defn list-objects [prefix]
  (map #(.substring (.toString %) (+ 1 (.length (.toString data-path)))) (->> prefix
                                                                              (.resolve data-path)
                                                                              .toFile
                                                                              .listFiles)))

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
      {:status 200
       :body data}
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
        {:status 200
         :body {:message "OK"}}
        {:status 409
         :body {:message "Conflict: tags are immutable"}}
        )
      (do
        (put-object path bytes)
        {:body {:message "OK"}}))))

(defn read-tag [path]
  (let [basename (last (.split path "/"))
        tag (.substring basename 0 (- (.length basename) 5))]
    {tag (json/parse-string (String. (get-object path)))}))

(defn get-tags [request]
  (let [repo1 (get-in request [:parameters :path :repo1])
        repo2 (get-in request [:parameters :path :repo2])
        path (str repo1 "/" repo2 "/tags/")
        tag-paths (list-objects path)]
    (log/info tag-paths)
    (log/info (reduce assoc (map read-tag tag-paths)))
    {:body (reduce assoc (map read-tag tag-paths))}))

(defn put-images [request]
  "this is the final call from Docker client when pushing an image
   Docker client expects HTTP status code 204 (No Content) instead of 200 here!"
  (let [repo1 (get-in request [:parameters :path :repo1])
        repo2 (get-in request [:parameters :path :repo2])
        path (str repo1 "/" repo2 "/images.json")
        ]
    ; TODO: the body is actually empty/useless here
    (put-object path (-> request :body json/generate-string (.getBytes "UTF-8")))
    {:status 204
     :body ""}))

(defn get-images [request]
  {:status 200
   :body "[]"}
  )

(defn get-ancestry
  ([image-id]
    (get-ancestry image-id []))
  ([image-id ancestry]
    (let [parent (get (get-image-json-data image-id) "parent")
          new-ancestry (conj ancestry image-id)]
      (if parent
        (get-ancestry parent new-ancestry)
        new-ancestry
        )

      )))

(defn get-image-ancestry [request]
  (let [image-id (get-in request [:parameters :path :image])]
    (log/info (get-ancestry image-id))
    {:body (json/generate-string (get-ancestry image-id))}
    ))

(def app
  (-> (s1st/swagger-executor)
      (s1st/swagger-validator)
      (s1st/swagger-parser)
      (s1st/swagger-mapper ::s1st/yaml-cp "api.yaml")
      (wrap-json-body)
      (wrap-json-response)
      (wrap-params)))

