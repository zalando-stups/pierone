(ns org.zalando.stups.pierone.core-test
  (:require [clojure.test :refer :all]
            [clj-http.lite.client :as client]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [org.zalando.stups.pierone.core :refer [run]]
            [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json]))

(def test-url "http://localhost:8080")

(def test-images-hierarchy
  [{:id       "img1"
    :metadata "{\"id\": \"img1\", \"parent\": \"img2\"}"
    :data     (io/input-stream (.getBytes "img1data"))}
   {:id       "img2"
    :metadata "{\"id\": \"img2\", \"parent\": \"img3\"}"
    :data     (io/input-stream (.getBytes "img2data"))}
   {:id       "img3"
    :metadata "{\"id\": \"img3\"}"
    :data     (io/input-stream (.getBytes "img3data"))}])

(def test-image-single
  {:id       "img4"
   :metadata "{\"id\": \"img4\"}"
   :data     (io/input-stream (.getBytes "img4data"))})

(def all-images
  (conj test-images-hierarchy test-image-single))

(def test-tag
  {:team     "stups"
   :artifact "kio"
   :name     "1.0"})

(def test-tag-snapshot
  {:team     "stups"
   :artifact "kio"
   :name     "1.0-SNAPSHOT"})

(def all-tags (conj [] test-tag test-tag-snapshot))

(defn url [& path]
  (apply str test-url "/v1" path))

(defn expect [msg http-code result]
  (is (= (:status result) http-code) (str msg ":" (:body result)))
  (is (= "0.6.3" (get (:headers result) "x-docker-registry-version")) msg)
  (is (= "localhost:8080" (get (:headers result) "x-docker-endpoints")) msg)
  (:body result))

; TODO aws tests?
(defn setup []
  (let [system (run {})]
    (doseq [tag all-tags]
      (jdbc/delete! (:db system) :tag ["team = ? AND artifact = ? AND name = ?" (:team tag) (:artifact tag) (:name tag)])
      (println "Deleted tag" (:team tag) "/" (:artifact tag) ":" (:name tag) "from old tests if existed."))
    (doseq [image all-images]
      (jdbc/delete! (:db system) :image ["id = ?" (:id image)])
      (println "Deleted image" (:id image) "from old tests if existed."))
    system))

(deftest integration-tests

  ; setup system
  (let [system (setup)]

    ; v2 compatibility check
    (let [result (client/get (str test-url "/v2/") {:throw-exceptions false})]
      (= 404 (:status result) "v2 compatibility"))

    ; v1 compatibility check
    (expect "ping" 200 (client/get (url "/_ping") {:throw-exceptions false}))

    ; push all images
    (doseq [image test-images-hierarchy]
      (expect "no metadata"
              404 (client/get (url "/images/" (:id image) "/json")
                              {:throw-exceptions false}))
      (expect "upload metadata"
              200 (client/put (url "/images/" (:id image) "/json")
                              {:body             (:metadata image)
                               :throw-exceptions false
                               :content-type     :json}))
      (expect "upload data"
              200 (client/put (url "/images/" (:id image) "/layer")
                              {:body             (:data image)
                               :throw-exceptions false})))

    ; push image again -> fail
    (let [again (first test-images-hierarchy)]
      (expect "push again"
              409 (client/put (url "/images/" (:id again) "/json")
                              {:body             (:metadata again)
                               :throw-exceptions false
                               :content-type     :json})))
    ; pull images -> all images available
    (doseq [image test-images-hierarchy]
      (expect "pull metadata"
              200 (client/get (url "/images/" (:id image) "/json")
                              {:throw-exceptions false}))
      (let [body (expect "pull metadata"
                         200 (client/get (url "/images/" (:id image) "/layer")
                                         {:throw-exceptions false}))]
        (= body (:data image))))

    ; check ancestry -> all images in ancestry
    (let [root (first test-images-hierarchy)
          ancestry (expect "pull metadata"
                           200 (client/get (url "/images/" (:id root) "/ancestry")
                                           {:throw-exceptions false}))
          ancestry (into #{} ancestry)]
      (= (count ancestry) (count test-images-hierarchy))
      (doseq [image test-image-single]
        (= true (ancestry (:id image)))))

    ; push image metadata without binary data does not block a new upload
    (expect "upload metadata"
            200 (client/put (url "/images/" (:id test-image-single) "/json")
                            {:body             (:metadata test-image-single)
                             :throw-exceptions false
                             :content-type     :json}))
    (expect "upload metadata"
            200 (client/put (url "/images/" (:id test-image-single) "/json")
                            {:body             (:metadata test-image-single)
                             :throw-exceptions false
                             :content-type     :json}))
    ; pulling an unfinished image isn't possible
    (expect "no metadata"
            404 (client/get (url "/images/" (:id test-image-single) "/json")
                            {:throw-exceptions false}))

    (let [root (first test-images-hierarchy)
          alternative (second test-images-hierarchy)]

      ; tag image -> ok
      (expect "tag release"
              200 (client/put (url "/repositories/" (:team test-tag) "/" (:artifact test-tag) "/tags/" (:name test-tag))
                              {:body             (str "\"" (:id root) "\"")
                               :content-type     :json
                               :throw-exceptions false}))

      ; tag image again -> not ok
      (expect "tag release again"
              409 (client/put (url "/repositories/" (:team test-tag) "/" (:artifact test-tag) "/tags/" (:name test-tag))
                              {:body             (str "\"" (:id alternative) "\"")
                               :content-type     :json
                               :throw-exceptions false}))

      ; check tag list
      (let [result (json/read-str (expect "list tags"
                           200 (client/get (url "/repositories/" (:team test-tag) "/" (:artifact test-tag) "/tags")
                                           {:throw-exceptions false})))]
        (= (count result) 1 "list tags: count")
        (println result)
        (let [[tag image] (first result)]
          (= tag (:name test-tag) "list tags: tag")
          (= image (:id root) "list tags: image")))

      ; tag -SNAPSHOT image -> ok
      (expect "tag snapshot"
              200 (client/put (url "/repositories/" (:team test-tag-snapshot) "/" (:artifact test-tag-snapshot) "/tags/" (:name test-tag-snapshot))
                              {:body             (str "\"" (:id root) "\"")
                               :content-type     :json
                               :throw-exceptions false}))

      ; tag -SNAPSHOT image again -> ok
      (expect "tag snapshot again"
              200 (client/put (url "/repositories/" (:team test-tag-snapshot) "/" (:artifact test-tag-snapshot) "/tags/" (:name test-tag-snapshot))
                              {:body             (str "\"" (:id alternative) "\"")
                               :content-type     :json
                               :throw-exceptions false})))

    ; dummy calls that have to exist
    (expect "search" 200 (client/get (url "/search")
                                     {:throw-exceptions false})) ; TODO implement search
    (expect "create repositories" 200 (client/put (url "/repositories/ateam/anartifact/")
                                                  {:throw-exceptions false}))
    (expect "put images" 204 (client/put (url "/repositories/ateam/anartifact/images")
                                         {:throw-exceptions false}))
    (expect "get images" 200 (client/get (url "/repositories/ateam/anartifact/images")
                                         {:throw-exceptions false}))
    (expect "put checksum" 200 (client/put (url "/images/animage/checksum")
                                           {:throw-exceptions false}))

    (component/stop system)))
