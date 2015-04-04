(ns org.zalando.stups.pierone.core-test
  (:require [clojure.test :refer :all]
            [clj-http.lite.client :as client]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [org.zalando.stups.pierone.core :refer [run]]))

(def test-url "http://localhost:8080")

(def test-images
  {:img1 {:id       "img1"
          :metadata "{\"id\": \"img1\", \"parent\": \"img2\"}"
          :data     (io/input-stream (.getBytes "img1data"))}
   :img2 {:id       "img2"
          :metadata "{\"id\": \"img2\", \"parent\": \"img3\"}"
          :data     (io/input-stream (.getBytes "img2data"))}
   :img  {:id       "img3"
          :metadata "{\"id\": \"img3\"}"
          :data     (io/input-stream (.getBytes "img3data"))}})

(defn url [& path]
  (apply str test-url "/v1" path))

(defn expect [msg http-code result]
  (is (= (:status result) http-code) msg)
  (is (= "0.6.3" (get (:headers result) "x-docker-registry-version")) msg)
  (is (= "localhost:8080" (get (:headers result) "x-docker-endpoints")) msg)
  (:body result))

(deftest integration-tests

  ; setup system
  (let [system (run {})]

    ; v1 compatibility check
    (expect "ping" 200 (client/get (url "/_ping") {:throw-exceptions false}))

    ; push some images
    (doseq [[_ img] test-images]
      (expect "no metadata"
              404 (client/get (url "/images/" (:id img) "/json") {:throw-exceptions false}))
      (expect "upload metadata"
              200 (client/put (url "/images/" (:id img) "/json") {:body (:metadata img) :throw-exceptions false}))
      (expect "upload data"
              200 (client/put (url "/images/" (:id img) "/layer") {:body (:data img) :throw-exceptions false})))

    (component/stop system)))
