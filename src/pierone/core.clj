(ns pierone.core
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-response]]))

(defn check-v2 [request]
  {:status 404
   :body {:message "Registry API v2 is not implemented"}})

(defn index [request]
  {:body {"Welcome" "to Pier One"}})

(defn ping [request]
  {:status  200
   :headers {"Content-Type" "application/json"
             "X-Docker-Registry-Version" "0.6.3"}
   :body    "true"})

(defn put-repo [request]
  {:headers {"X-Docker-Token" "FakeToken"
             "X-Docker-Endpoints" (get-in request [:headers "host"])}
   :body {:message "OK"}})

(defn get-image-json [request]
  {:status 404
   :body {:message "TODO" :image (get-in request [:parameters "image" request])}})

(def app
  (-> (s1st/swagger-executor)
      (s1st/swagger-validator)
      (s1st/swagger-parser)
      (s1st/swagger-mapper ::s1st/yaml-cp "api.yaml")
      (wrap-json-response)
      (wrap-params)))
