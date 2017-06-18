(ns org.zalando.stups.pierone.core
  (:require [com.stuartsierra.component :refer [using system-map]]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.friboo.system :as system]
            [org.zalando.stups.friboo.system.audit-logger.http :as httplogger]
            [org.zalando.stups.friboo.system.oauth2 :as oauth2]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.pierone.api :as api]
            [org.zalando.stups.pierone.sql :as sql]
            [org.zalando.stups.pierone.clair :as clair]
            [org.zalando.stups.pierone.storage :as storage]
            [com.stuartsierra.component :as component]
            [org.zalando.stups.pierone.lib.nrepl :as nrepl])
  (:gen-class))

(defn run
  "Initializes and starts the whole system."
  [default-configuration]
  (let [configuration (config/load-configuration
                        (system/default-http-namespaces-and :storage :db :api :httplogger :oauth2)
                        [api/default-http-configuration
                         sql/default-db-configuration
                         storage/default-storage-configuration
                         default-configuration])
        storage-engine (if (contains? (:storage configuration) :s3-bucket)
                         storage/map->S3Storage
                         storage/map->LocalStorage)
        system (system/http-system-map configuration
                                       api/map->API
                                       [:storage :db :api-config :httplogger]
                                       :api-config (:api configuration)
                                       :tokens (oauth2/map->OAUth2TokenRefresher {:configuration (:oauth2 configuration)
                                                                                  :tokens        {"http-audit-logger" ["uid"]}})
                                       :httplogger (component/using
                                                     (httplogger/map->HTTP {:configuration (merge (:httplogger configuration)
                                                                                                  {:token-name "http-audit-logger"})})
                                                     [:tokens])
                                       :clair-receiver (component/using (clair/make-clair-receiver) [:db :api-config])
                                       :storage (storage-engine {:configuration (:storage configuration)})
                                       :db (sql/map->DB {:configuration (:db configuration)}))]
    (system/run configuration system)))

;; Running system (when started from -main)
;; During development please use user/system
(defonce system nil)

(defn stop []
  (when system
    (let [stopped-system (component/stop system)]
      (alter-var-root #'system (constantly stopped-system)))))

(defn start
  ([]
   (start {}))
  ([override-env]
   (with-redefs [environ.core/env (merge environ.core/env override-env)]
     (let [started-system (run {})]
       (alter-var-root #'system (constantly started-system))))))

(defn restart
  ([]
   (restart {}))
  ([override-env]
   (stop)
   (start override-env)))

(defn -main
  "The actual main for our uberjar."
  [& args]
  (try
    (nrepl/start-nrepl)
    (start)
    (catch Exception e
      (log/error e "Could not start system because of %s." (str e))
      (System/exit 1))))

(comment
  ;; Restart the app while overriding some of the env vars
  (restart {}))
