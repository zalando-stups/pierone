(ns pierone.backend.file
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [pierone.backend :refer [Backend as-bytes]])
  (:import
    [java.nio.file.attribute FileAttribute]
    [java.nio.file Files]
    [java.nio.file OpenOption]
    [java.nio.file Paths]
    [java.nio.file Path]))

(defn create-dirs [path]
  (Files/createDirectories path (into-array FileAttribute [])))


(defrecord FileBackend [^Path data-path]
  component/Lifecycle

  (start [this]
    (log/info "Starting file backend using data path" (str data-path))
    this)

  (stop [this] this)

  Backend

  (put-object [{:keys [data-path]} key stream-or-bytes]
    (let [full-path (.resolve data-path key)]
      (create-dirs (.getParent full-path))
      (Files/write full-path (as-bytes stream-or-bytes) (make-array OpenOption 0))))

  (get-object [{:keys [data-path]} key]
    (try
      (Files/readAllBytes (.resolve data-path key))
      (catch java.nio.file.NoSuchFileException e nil)))

  (list-objects [{:keys [data-path]} prefix]
    (map #(.substring (str %) (inc (count (str data-path)))) (->> prefix
                                                                  (.resolve data-path)
                                                                  .toFile
                                                                  .listFiles))))

(defn new-file-backend []
  (map->FileBackend {:data-path (Paths/get "data" (make-array String 0))}))