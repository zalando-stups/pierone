(ns org.zalando.stups.pierone.core
  (:require [com.stuartsierra.component :refer [using system-map]]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.friboo.system :as system]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.pierone.api :as api]
            [org.zalando.stups.pierone.backend.file :as file]
            [org.zalando.stups.pierone.backend.s3 :as s3])
  (:gen-class))

(defn new-system [configuration]
  (system-map

    :backend (if-let [bucket (:s3-bucket-name (:backend configuration))]
               (s3/map->S3Backend {:bucket bucket})
               (file/map->FileBackend {}))

    :api (using
           (api/map->API {:configuration (:http configuration)})
           [:backend])))

(defn run
  "Initializes and starts the whole system."
  [default-configuration]
  (let [configuration (config/load-configuration
                        [:backend :http]
                        [api/default-http-configuration
                         default-configuration])
        system (new-system configuration)]
    (system/run configuration system)))

(defn -main
  "The actual main for our uberjar."
  [& args]
  (try
    (run {})
    (catch Exception e
      (log/error e "Could not start system because of %s." (str e))
      (System/exit 1))))