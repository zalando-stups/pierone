(ns pierone.backend)

(defprotocol Backend
  (list-objects [this prefix] "lists all objects starting with prefix")
  (put-object [this key bytes] "stores an object")
  (get-object [this key] "retrieves an object"))
