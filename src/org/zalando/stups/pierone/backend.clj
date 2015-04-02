(ns org.zalando.stups.pierone.backend
  (:import (java.io ByteArrayInputStream InputStream)
           (org.apache.commons.io IOUtils)))

(defprotocol Backend
  (list-objects [this prefix] "lists all objects starting with prefix")
  (put-object [this key stream-or-bytes] "stores an object")
  (get-object [this key] "retrieves an object"))


(defn as-stream [stream-or-bytes]
  (if (instance? InputStream stream-or-bytes)
    stream-or-bytes
    (ByteArrayInputStream. stream-or-bytes)))

(defn as-bytes [stream-or-bytes]
  (if (instance? InputStream stream-or-bytes)
    (IOUtils/toByteArray stream-or-bytes)
    stream-or-bytes))
