(ns pierone.backend.s3
  (:require [amazonica.aws.s3 :as aws]
            [com.stuartsierra.component :as component]
            [pierone.backend :refer [Backend as-stream]]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:import (java.io ByteArrayInputStream InputStream)
           (com.amazonaws.services.s3.model AmazonS3Exception PutObjectRequest ObjectMetadata)
           (com.amazonaws.services.s3 AmazonS3Client)
           (com.amazonaws.regions Region Regions)))

(defn invoke-s3-put [client request]
  (.putObject client request))

(defn put-s3-object [aws-region-id bucket-name key stream]
  (let [client (doto (AmazonS3Client.)
                 (.setRegion
                   (->> (-> (str/upper-case aws-region-id)
                            (str/replace "-" "_"))
                        Regions/valueOf
                        Region/getRegion)))
        request (PutObjectRequest. bucket-name key stream (ObjectMetadata. ))]
    (-> request
        .getRequestClientOptions
        (.setReadLimit (* 512 1014 1014)))
    (invoke-s3-put client request)))

(defrecord S3Backend [bucket-name aws-region-id]
  component/Lifecycle

  (start [this]
    (log/info "Starting S3 backend using bucket" bucket-name "in region" aws-region-id)
    this)

  (stop [this] this)

  Backend

  (put-object [{:keys [bucket-name]} key stream-or-bytes]
    (put-s3-object aws-region-id bucket-name key (as-stream stream-or-bytes)))

  (get-object [{:keys [bucket-name]} key]
    (try
      (-> (aws/get-object {:endpoint aws-region-id}
                          :bucket-name bucket-name
                          :key key)
          :input-stream
          org.apache.commons.io.IOUtils/toByteArray)
      (catch AmazonS3Exception se
        (when-not (= 404 (.getStatusCode se)) (throw)))))

  (list-objects [{:keys [bucket-name]} prefix]
    (->> (aws/list-objects {:endpoint aws-region-id}
                           :bucket-name bucket-name
                           :key-prefix prefix)
         :object-summaries
         (map :key)
         (filter #(.startsWith % prefix))
         seq)))

(defn new-s3-backend [bucket-name aws-region-id]
  (map->S3Backend {:bucket-name bucket-name :aws-region-id aws-region-id}))