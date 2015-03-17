(ns pierone.backend.s3
  (:require [amazonica.aws.s3 :as aws]
            [com.stuartsierra.component :as component]
            [pierone.backend :refer [Backend]]
            [clojure.tools.logging :as log])
  (:import (java.io ByteArrayInputStream)
           (com.amazonaws.services.s3.model AmazonS3Exception)))

(defrecord S3Backend [bucket-name]
  component/Lifecycle

  (start [this]
    (log/info "Starting S3 backend using bucket" bucket-name)
    this)

  (stop [this] this)

  Backend

  (put-object [{:keys [bucket-name]} key bytes]
    (aws/put-object :bucket-name bucket-name
                    :key key
                    :input-stream (ByteArrayInputStream. bytes)))

  (get-object [{:keys [bucket-name]} key]
    (try
      (-> (aws/get-object :bucket-name bucket-name
                          :key key)
          :input-stream
          org.apache.commons.io.IOUtils/toByteArray)
      (catch AmazonS3Exception se
        (if (= 404 (.getStatusCode se))
            nil
            (throw)))))

  (list-objects [{:keys [bucket-name]} prefix]
    (->> (aws/list-objects :bucket-name bucket-name
                           :key-prefix prefix)
         :object-summaries
         (map :key)
         (filter #(.startsWith % prefix))
         seq)))

(defn new-s3-backend [bucket-name]
  (map->S3Backend {:bucket-name bucket-name}))