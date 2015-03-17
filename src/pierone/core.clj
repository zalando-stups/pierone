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
            [pierone.backend.file :refer [new-file-backend]]))

(defn new-system [definition new-backend]
  (component/system-map

    ; our backend implementation
    :backend (new-backend)

    ; our API depends on the Backend
    :api (component/using
           (new-api definition) [:backend])))

(defn -main [& args]
  (log/info "Starting Pier One Docker Registry")
  (component/start
    (new-system "api.yaml" new-file-backend)))
