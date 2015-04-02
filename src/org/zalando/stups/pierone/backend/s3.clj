(ns org.zalando.stups.pierone.backend.s3
  (:require [amazonica.aws.s3 :as aws]
            [com.stuartsierra.component :as component]
            [org.zalando.stups.pierone.backend :refer [Backend as-stream]]
            [org.zalando.stups.friboo.log :as log]
            [clojure.string :as str])
  (:import (com.amazonaws.services.s3.model AmazonS3Exception PutObjectRequest ObjectMetadata)
           (com.amazonaws.services.s3 AmazonS3Client)
           (com.amazonaws.regions Region Regions)))

(defn invoke-s3-put [client request]
  (.putObject client request))

(defn put-s3-object [bucket-name key stream]
  (let [client (AmazonS3Client.)
        request (PutObjectRequest. bucket-name key stream (ObjectMetadata. ))]
    (-> request
        .getRequestClientOptions
        (.setReadLimit (* 512 1014 1014)))
    (invoke-s3-put client request)))

(defrecord S3Backend [bucket]
  component/Lifecycle

  (start [this]
    (log/info "Starting S3 backend with bucket %s." bucket)
    this)

  (stop [this] this)

  Backend

  (put-object [{:keys [bucket]} key stream-or-bytes]
    (put-s3-object bucket key (as-stream stream-or-bytes)))

  (get-object [{:keys [bucket]} key]
    (try
      (-> (aws/get-object :bucket-name bucket
                          :key key)
          :input-stream
          org.apache.commons.io.IOUtils/toByteArray)
      (catch AmazonS3Exception se
        (when-not (= 404 (.getStatusCode se)) (throw)))))

  (list-objects [{:keys [bucket]} prefix]
    (->> (aws/list-objects :bucket-name bucket
                           :key-prefix prefix)
         :object-summaries
         (map :key)
         (filter #(.startsWith % prefix))
         seq)))
