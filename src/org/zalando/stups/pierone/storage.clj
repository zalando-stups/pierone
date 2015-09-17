(ns org.zalando.stups.pierone.storage
  (:require [org.zalando.stups.friboo.log :as log]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [amazonica.aws.s3 :as s3])
  (:import (com.amazonaws.services.s3.model AmazonS3Exception)
           (java.util UUID)
           (java.io File)))

(defprotocol Storage
  (read-data [this image] "Returns the binary data for an image.")
  (write-data [this image data] "Stores binary data for an image."))


(def default-storage-configuration
  {:storage-directory "target/data"})


(defrecord LocalStorage [configuration directory]
  component/Lifecycle

  (start [this]
    (let [directory (:directory configuration)]
      (log/info "Using local storage with directory %s." directory)
      (.mkdirs (io/file directory))
      (assoc this :directory directory)))

  (stop [this]
    (assoc this :directory nil))

  Storage

  (read-data [_ image]
    (let [^File file (io/file directory image)]
      (when (.exists file)
            (io/input-stream file))))

  (write-data [_ image data]
    (let [^File file (io/file directory image)
          ^File tmp-file (io/file directory (str image ".tmp-" (UUID/randomUUID)))]
      (io/copy data tmp-file)
      (.renameTo tmp-file file))))


(defrecord S3Storage [configuration directory bucket]
  component/Lifecycle

  (start [this]
    (let [directory (:directory configuration)
          bucket (:s3-bucket configuration)]
      (log/info "Using S3 storage with bucket %s and temporary directory %s." bucket directory)
      (.mkdirs (io/file directory))
      (merge this {:directory directory
                   :bucket    bucket})))

  (stop [this]
    (merge this {:directory nil
                 :bucket    nil}))

  Storage

  (read-data [_ image]
    (try
      (let [result (s3/get-object :bucket-name bucket
                                  :key image)]
        (:input-stream result))
      (catch AmazonS3Exception se
        (when-not (= 404 (.getStatusCode se)) (throw se)))))

  (write-data [_ image data]
    (let [^File tmp-file (io/file directory (str image ".tmp-" (UUID/randomUUID)))]
      (io/copy data tmp-file)
      (s3/put-object :bucket-name bucket
                     :key image
                     :file tmp-file)
      (io/delete-file tmp-file true))))
