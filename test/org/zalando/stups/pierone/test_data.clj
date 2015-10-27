(ns org.zalando.stups.pierone.test-data
  (:require [clojure.java.io :as io]))

(def images-hierarchy
  [{:id       "abc1"
    :metadata "{\"id\": \"abc1\", \"parent\": \"def2\", \"key/with/slash\": \"test\"}"
    :data     (.getBytes "img1data")}
   {:id       "def2"
    :metadata "{\"id\": \"def2\", \"parent\": \"aaa3\"}"
    :data     (.getBytes "img2data")}
   {:id       "aaa3"
    :metadata "{\"id\": \"aaa3\"}"
    :data     (.getBytes "img3data")}])

(def image-single
  {:id       "fff4"
   :metadata "{\"id\": \"fff4\"}"
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
