(ns pierone.backend.s3
  (:require [amazonica.aws.s3 :as aws])
  (:import (java.io ByteArrayInputStream)))

(defrecord S3 [bucket-name])

(defn put-object [{:keys [bucket-name]} key bytes]
  (aws/put-object :bucket-name bucket-name
                  :key key
                  :input-stream (ByteArrayInputStream. bytes)))

(defn get-object [{:keys [bucket-name]} key]
  (-> (aws/get-object :bucket-name bucket-name
                      :key key)
      :input-stream
      org.apache.commons.io.IOUtils/toByteArray))

(defn list-objects [{:keys [bucket-name]} prefix]
  (->> (aws/list-objects :bucket-name bucket-name
                         :key-prefix prefix)
       :object-summaries
       (map :key)
       (filter #(.startsWith % prefix))
       seq))