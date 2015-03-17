(ns pierone.backend.s3
  (:require [amazonica.aws.s3 :as aws]
            [com.stuartsierra.component :as component]
            [pierone.backend :refer [Backend]])
  (:import (java.io ByteArrayInputStream)))

(defrecord S3Backend [bucket-name]
  component/Lifecycle

  (start [this] this)

  (stop [this] this)

  Backend

  (put-object [{:keys [bucket-name]} key bytes]
    (aws/put-object :bucket-name bucket-name
                    :key key
                    :input-stream (ByteArrayInputStream. bytes)))

  (get-object [{:keys [bucket-name]} key]
    (-> (aws/get-object :bucket-name bucket-name
                        :key key)
        :input-stream
        org.apache.commons.io.IOUtils/toByteArray))

  (list-objects [{:keys [bucket-name]} prefix]
    (->> (aws/list-objects :bucket-name bucket-name
                           :key-prefix prefix)
         :object-summaries
         (map :key)
         (filter #(.startsWith % prefix))
         seq)))