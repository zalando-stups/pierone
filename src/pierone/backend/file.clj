(ns pierone.backend.file
  (:require [com.stuartsierra.component :as component]
            [pierone.backend :refer [Backend]])
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

  (start [this] this)

  (stop [this] this)

  Backend

  (put-object [{:keys [data-path]} key bytes]
    (let [full-path (.resolve data-path key)]
      (create-dirs (.getParent full-path))
      (Files/write full-path bytes (make-array OpenOption 0))))

  (get-object [{:keys [data-path]} key]
    (try
      (Files/readAllBytes (.resolve data-path key))
      (catch java.nio.file.NoSuchFileException e nil)))

  (list-objects [{:keys [data-path]} prefix]
    (map #(.substring (str %) (+ 1 (count (str data-path)))) (->> prefix
                                                                  (.resolve data-path)
                                                                  .toFile
                                                                  .listFiles))))

(defn new-file-backend []
  (map->FileBackend {:data-path (Paths/get "data" (make-array String 0))}))