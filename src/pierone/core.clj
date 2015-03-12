(ns pierone.core
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]))

(defn create-greeting [request]
  {:status  200
   :headers {"content-type" "text/plain"}
   :body    "Hello!"})

(def app
  (-> (s1st/swagger-executor)
      (s1st/swagger-validator)
      (s1st/swagger-parser)
      (s1st/swagger-mapper ::s1st/yaml-cp "api.yaml")
      (wrap-params)))
