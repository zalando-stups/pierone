(ns org.zalando.stups.pierone.test-data
  (:require [clojure.java.io :as io]))

(def images-hierarchy
  [{:id       "img1"
    :metadata "{\"id\": \"img1\", \"parent\": \"img2\", \"key/with/slash\": \"test\"}"
    :data     (io/input-stream (.getBytes "img1data"))}
   {:id       "img2"
    :metadata "{\"id\": \"img2\", \"parent\": \"img3\"}"
    :data     (io/input-stream (.getBytes "img2data"))}
   {:id       "img3"
    :metadata "{\"id\": \"img3\"}"
    :data     (io/input-stream (.getBytes "img3data"))}])

(def image-single
  {:id       "img4"
   :metadata "{\"id\": \"img4\"}"
   :data     (io/input-stream (.getBytes "img4data"))})

(def all-images (conj images-hierarchy image-single))

(def tag
  {:team     "stups"
   :artifact "kio"
   :name     "1.0"})

(def latest-tag
  {:team     "stups"
   :artifact "kio"
   :name     "latest"})

(def snapshot-tag
  {:team     "stups"
   :artifact "kio"
   :name     "1.0-SNAPSHOT"})

(def all-tags (conj [] tag snapshot-tag))
