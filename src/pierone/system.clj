(ns pierone.system
    (:gen-class)
    (:require [com.stuartsierra.component :as component :refer [using]]
      [environ.core :refer [env]]
      ))


(defn new-system [config]
      "Returns a new instance of the whole application"
      (component/system-map))

(defn start [system]
      (component/start system))

(defn stop [system]
      (component/stop system))

(defn -main [& args]
      (let [system (new-system env)]

           (.addShutdownHook
             (Runtime/getRuntime)
             (Thread. (fn [] (stop system))))

           (start system)))
