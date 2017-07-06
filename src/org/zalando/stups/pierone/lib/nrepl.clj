(ns org.zalando.stups.pierone.lib.nrepl
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.nrepl.server :as n]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :as config]))

(defn flatten1
  "Flattens the collection one level, for example, converts {:a 1 :b 2} to (:a 1 :b 2)."
  [coll]
  (apply concat coll))

(defn start-component [{:as this :keys [configuration]}]
  (log/info "Starting NREPL server")
  (let [server (apply n/start-server (flatten1 configuration))]
    (log/info "NREPL server is listening on %s" (str (:server-socket server)))
    (assoc this :server server)))

(defn stop-component [{:as this :keys [server]}]
  (log/info "Stopping NREPL server")
  (when server
    (n/stop-server server))
  (dissoc this :server))

(defrecord NREPL [configuration server]
  component/Lifecycle
  (start [this]
    (start-component this))
  (stop [this]
    (stop-component this)))

;; NREPL server - has to be outside of the main system to allow restarting
(defonce nrepl-server nil)

(defn run-nrepl []
  ;; :nrepl-bind defaults to "::", which might not be supported in some environments
  (let [config (config/load-configuration [:nrepl] [{:nrepl-bind "0.0.0.0"}])]
    (when (-> config :nrepl :enabled)
      (let [nrepl (map->NREPL {:configuration (:nrepl config)})]
        (component/start nrepl)))))

(defn start-nrepl []
  (let [started-nrepl (run-nrepl)]
    (alter-var-root #'nrepl-server (constantly started-nrepl))))
