(ns pierone.core
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-body]]
            [com.stuartsierra.component :as component]
            [ring.util.response :refer :all]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [cheshire.core :as json]
            [pierone.api :refer [new-api]]
            [pierone.backend.file :refer [new-file-backend]]
            [pierone.backend.s3 :refer [new-s3-backend]]))

(defn new-system [config]
  (component/system-map

    ; our backend implementation
    :backend (case (:backend config)
               ("s3") (new-s3-backend (:s3-bucket-name config))
               ("file") (new-file-backend)
               (new-file-backend))

    ; our API depends on the Backend
    :api (component/using
           (new-api "api.yaml") [:backend])))

(defn -main [& args]
  (log/info "Starting Pier One Docker Registry")
  (component/start
    (new-system env)))
