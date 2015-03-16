(ns pierone.backend.file
  (:import
    [java.nio.file.attribute FileAttribute]
    [java.nio.file Files]
    [java.nio.file OpenOption]
    [java.nio.file Paths]))

(def data-path (Paths/get "data" (into-array String [])))

(defn create-dirs [path]
  (Files/createDirectories path (into-array FileAttribute [])))

(defn put-object [key bytes]
  (let [full-path (.resolve data-path key)]
    (create-dirs (.getParent full-path))
    (Files/write full-path bytes (into-array OpenOption []))))

(defn get-object [key]
  (try
    (Files/readAllBytes (.resolve data-path key))
    (catch java.nio.file.NoSuchFileException e nil)))

(defn list-objects [prefix]
  (map #(.substring (.toString %) (+ 1 (.length (.toString data-path)))) (->> prefix
                                                                              (.resolve data-path)
                                                                              .toFile
                                                                              .listFiles)))