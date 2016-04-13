(ns org.zalando.stups.pierone.test-data
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]))

(def json-opts {:pretty {:indentation                  3
                         :object-field-value-separator ": "
                         :indent-arrays?               true
                         :indent-objects?              true}})

(defn load-manifest [path]
  (let [file    (slurp path)
        content (json/parse-string file true)
        pretty  (json/encode content json-opts)
        digest  (-> content :fsLayers first :blobSum)
        bytes   (.getBytes pretty)]
    {:file    file
     :content content
     :digest  digest
     :pretty  pretty
     :bytes   bytes}))

(def manifest-v1-multilayer (load-manifest "test/org/zalando/stups/pierone/manifest-multilayer.json"))
(def manifest-v1 (load-manifest "test/org/zalando/stups/pierone/manifest.json"))
(def manifest-v2 (load-manifest "test/org/zalando/stups/pierone/manifestv2.json"))
(def manifest-v4 (load-manifest "test/org/zalando/stups/pierone/manifestv4.json"))

(def images-hierarchy
     [{:id       "abc1"
       :metadata "{\"id\": \"abc1\", \"parent\": \"def2\", \"key/with/slash\": \"test\"}"
       :data     (.getBytes "img1data")}
      {:id       "def2"
       :metadata "{\"id\": \"def2\", \"parent\": \"abc3\"}"
       :data     (.getBytes "img2data")}
      {:id       "abc3"
       :metadata "{\"id\": \"abc3\"}"
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
