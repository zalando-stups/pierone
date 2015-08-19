(ns org.zalando.stups.pierone.v1-test
  (:require [org.zalando.stups.pierone.test-data :as d]
            [org.zalando.stups.pierone.test-utils :as u]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [com.stuartsierra.component :as component]))

(deftest v1-test
  (let [system (u/setup)]

    (u/delete-test-data system)
    
    ; ping
    (u/expect 200 (client/get (u/v1-url "/_ping")
                              (u/http-opts)))

    (u/push-images d/images-hierarchy)

    ; push again won't work
    (let [again (first d/images-hierarchy)]
      (u/expect 409
                (client/put (u/v1-url "/images/"
                                      (:id again)
                                      "/json")
                            (u/http-opts (:metadata again)
                                         :json))))

    ; pull and verify
    (doseq [image d/images-hierarchy]
      (let [body (u/expect 200
                           (client/get (u/v1-url "/images/"
                                                 (:id image)
                                                 "/json")
                                       (u/http-opts)))]
        (is (= (json/read-str body)
               (json/read-str (:metadata image)))))
      (let [response (client/get (u/v1-url "/images/"
                                           (:id image)
                                           "/layer")
                                 (merge (u/http-opts)
                                        {:as :byte-array}))
            body (:body response)]
        (u/expect 200
                  response)
        (is (= "application/octet-stream"
               (get (:headers response)
                    "content-type")))

        (is (= (String. body)
               (String. (:data image))))))
    ; verify ancestry
    (let [root (first d/images-hierarchy)
          ancestry (u/expect 200
                             (client/get (u/v1-url "/images/"
                                                   (:id root)
                                                   "/ancestry")
                                         (u/http-opts)))
          ancestry (into #{}(json/read-str ancestry))]
      (is (= (count ancestry)
             (count d/images-hierarchy)))
      (doseq [image d/images-hierarchy]
        (is (contains? ancestry
                       (:id image)))))

    ; push image metadata without binary data does not block a new upload
    (u/expect 200
              (client/put (u/v1-url "/images/"
                                    (:id d/image-single)
                                    "/json")
                          (u/http-opts (:metadata d/image-single)
                                       :json)))
    (u/expect 200
              (client/put (u/v1-url "/images/"
                                    (:id d/image-single)
                                    "/json")
                          (u/http-opts (:metadata d/image-single)
                                       :json)))
    ; pulling an unfinished image isn't possible
    (u/expect 404
              (client/get (u/v1-url "/images/"
                                    (:id d/image-single)
                                    "/json")
                          (u/http-opts)))

    ; test tagging
    (let [root (first d/images-hierarchy)
          alternative (second d/images-hierarchy)]
      ; tag image -> ok
      (u/expect 200
                (client/put (u/v1-url "/repositories/" (:team d/tag)
                                      "/" (:artifact d/tag)
                                      "/tags/" (:name d/tag))
                            (u/http-opts (u/wrap-quotes (:id root))
                                         :json)))
      ; tag image again -> not ok
      (u/expect 409
                (client/put (u/v1-url "/repositories/" (:team d/tag)
                                      "/" (:artifact d/tag)
                                      "/tags/" (:name d/tag))
                            (u/http-opts (u/wrap-quotes (:id alternative))
                                         :json)))
      ; tag -SNAPSHOT image -> ok
      (u/expect 200
                (client/put (u/v1-url "/repositories/" (:team d/snapshot-tag)
                                      "/" (:artifact d/snapshot-tag)
                                      "/tags/" (:name d/snapshot-tag))
                            (u/http-opts (u/wrap-quotes (:id root))
                                         :json)))

      ; tag -SNAPSHOT image again -> ok
      (is (= "OK"
             (u/expect 200
                       (client/put (u/v1-url "/repositories/" (:team d/snapshot-tag)
                                             "/" (:artifact d/snapshot-tag)
                                             "/tags/" (:name d/snapshot-tag))
                                   (u/http-opts (u/wrap-quotes (:id alternative))
                                                :json)))))

      ; tag -SNAPSHOT with same image
      (is (= "tag not modified"
             (u/expect 200
                       (client/put (u/v1-url "/repositories/" (:team d/snapshot-tag)
                                             "/" (:artifact d/snapshot-tag)
                                             "/tags/" (:name d/snapshot-tag))
                                   (u/http-opts (u/wrap-quotes (:id alternative))
                                                :json)))))
      ; tag latest -> not ok (to avoid mistakenly creating an immutable latest)
      (u/expect 409
                (client/put (u/v1-url "/repositories/" (:team d/latest-tag)
                                      "/" (:artifact d/latest-tag)
                                      "/tags/" (:name d/latest-tag))
                            (u/http-opts (u/wrap-quotes (:id alternative))
                                         :json)))

      ; check tag list for existing artifact -> ok
      (let [resp (u/expect 200 (client/get (u/v1-url "/repositories/" (:team d/tag)
                                                     "/" (:artifact d/tag)
                                                     "/tags")
                                           (u/http-opts)))
            result (json/read-str resp)]
        ; contains the test tag, snapshot tag and virtual "latest" tag
        (is (= (count result)
               3))

        (let [[latest-tag latest-image] (first result)
              [snapshot-tag snapshot-image] (second result)
              [real-tag real-image] (last result)]
          (is (= real-tag
                 (:name d/tag)))
          (is (= real-image
                 (:id root)))
          (is (= snapshot-tag
                 (:name d/snapshot-tag)))
          (is (= snapshot-image
                 (:id alternative)))
          (is (= latest-tag
                 "latest"))
          (is (= latest-image
                 snapshot-image))))

      ; check get image for single tag
      (is (= (u/wrap-quotes (:id root))
             (u/expect 200 (client/get (u/v1-url "/repositories/" (:team d/tag)
                                                 "/" (:artifact d/tag)
                                                 "/tags/" (:name d/tag))
                                       (u/http-opts)))))

      (is (= (u/wrap-quotes (:id alternative))
             (u/expect 200
                       (client/get (u/v1-url "/repositories/" (:team d/tag)
                                             "/" (:artifact d/tag)
                                             "/tags/latest")
                                   (u/http-opts)))))

      (is (= "not found"
             (u/expect 404
                       (client/get (u/v1-url "/repositories/" (:team d/tag)
                                             "/" (:artifact d/tag)
                                             "/tags/asdf")
                                   (u/http-opts))))))

    ; dummy calls that have to exist
    (u/expect 200 (client/get (u/v1-url "/search")
                              (u/http-opts)))
    (u/expect 200 (client/put (u/v1-url "/repositories/ateam/anartifact/")
                              (u/http-opts)))
    (u/expect 204 (client/put (u/v1-url "/repositories/ateam/anartifact/images")
                              (u/http-opts)))
    (u/expect 200 (client/get (u/v1-url "/repositories/ateam/anartifact/images")
                              (u/http-opts)))
    (u/expect 200 (client/put (u/v1-url "/images/animage/checksum")
                              (u/http-opts)))

    ; shutdown
    (component/stop system)))