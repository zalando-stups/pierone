(ns pierone.core
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-response]]))

(defn index [request]
  {:body {"Welcome" "to Pier One"}})

(defn ping [request]
  {:status  200
   :headers {"Content-Type" "application/json"
             "X-Docker-Registry-Version" "0.6.3"}
   :body    "true"})

(def app
  (-> (s1st/swagger-executor)
      (s1st/swagger-validator)
      (s1st/swagger-parser)
      (s1st/swagger-mapper ::s1st/yaml-cp "api.yaml")
      (wrap-json-response)
      (wrap-params)))
