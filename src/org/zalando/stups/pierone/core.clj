(ns org.zalando.stups.pierone.core
  (:require [com.stuartsierra.component :refer [using system-map]]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.friboo.system :as system]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.pierone.api :as api]
            [org.zalando.stups.pierone.sql :as sql]
            [org.zalando.stups.pierone.storage :as storage])
  (:gen-class))

(defn run
  "Initializes and starts the whole system."
  [default-configuration]
  (System/setProperty "hystrix.command.default.execution.timeout.enabled" "false")
  (let [configuration (config/load-configuration
                        (system/default-http-namespaces-and :storage :db)
                        [api/default-http-configuration
                         sql/default-db-configuration
                         storage/default-storage-configuration
                         default-configuration])
        storage-engine (if (contains? (:storage configuration) :s3-bucket)
                           storage/map->S3Storage
                           storage/map->LocalStorage)
        system (system/http-system-map configuration
                                       api/map->API
                                       [:storage :db]
                                       :storage (storage-engine {:configuration (:storage configuration)})
                                       :db      (sql/map->DB {:configuration (:db configuration)}))]
    (system/run configuration system)))

(defn -main
  "The actual main for our uberjar."
  [& args]
  (try
    (run {})
    (catch Exception e
      (log/error e "Could not start system because of %s." (str e))
      (System/exit 1))))