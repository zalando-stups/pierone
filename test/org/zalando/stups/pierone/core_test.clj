(ns org.zalando.stups.pierone.core-test
  (:require [clojure.test :refer :all]
            [clj-http.lite.client :as client]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [org.zalando.stups.pierone.core :refer [run]]
            [clojure.java.jdbc :as jdbc]))

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

; TODO use embedded db (h2, postgresql mode)
; TODO ?? use nolisten? true configuration and only handler?? maybe not in order to also test jetty behaviour
; TODO ?? use in-memory storage implementation ?? maybe not in order to have real impl covered
; TODO aws tests?
(defn setup []
  (let [system (run {})]
    (doseq [[_ image] test-images]
      (jdbc/delete! (:db system) :image ["id = ?" (:id image)])
      (println "Deleted image" (:id image) "from old tests."))
    system))

(deftest integration-tests

  ; setup system
  (let [system (setup)]

    ; v1 compatibility check
    (expect "ping" 200 (client/get (url "/_ping") {:throw-exceptions false}))

    ; push all images
    (doseq [[_ img] test-images]
      (expect "no metadata"
              404 (client/get (url "/images/" (:id img) "/json") {:throw-exceptions false}))
      (expect "upload metadata"
              200 (client/put (url "/images/" (:id img) "/json") {:body (:metadata img)
                                                                  :throw-exceptions false
                                                                  :content-type :json}))
      (expect "upload data"
              200 (client/put (url "/images/" (:id img) "/layer") {:body (:data img)
                                                                   :throw-exceptions false})))

    ; TODO push image again -> fail
    ; TODO push image metadata -> ok
    ; TODO push image metadata again -> ok

    ; TODO check ancestry -> all images in ancestry

    ; TODO pull images -> all images available

    ; TODO tag image -> ok
    ; TODO tag image again -> not ok

    ; TODO tag -SNAPSHOT image -> ok
    ; TODO tag -SNAPSHOT image again -> ok

    (component/stop system)))
