(ns org.zalando.stups.pierone.v1-test
  (:require [org.zalando.stups.pierone.test-data :as d]
            [org.zalando.stups.pierone.test-utils :as u]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.system.oauth2 :as oauth2]))

(deftest ^:integration v1-test
  (with-redefs [oauth2/map->OAUth2TokenRefresher u/map->NoTokenRefresher]
    (let [system (u/setup)]

    (u/wipe-db system)

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
        (is (= (json/parse-string body)
               (json/parse-string (:metadata image)))))
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
          ancestry (into #{}(json/parse-string ancestry))]
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
      ; empty list of tags -> 404
      (u/expect 404 (client/get (u/v1-url "/repositories/" (:team d/tag)
                                          "/non-existing"
                                          "/tags")
                                (u/http-opts)))

      ; check tag list for existing artifact -> ok
      (let [resp (u/expect 200 (client/get (u/v1-url "/repositories/" (:team d/tag)
                                                     "/" (:artifact d/tag)
                                                     "/tags")
                                           (u/http-opts)))
            result (json/parse-string resp)
            tags-in-result (set (map first result))]
        ; contains the test tag, snapshot tag and virtual "latest" tag
        (is (= (count result)
               3))
        (is (contains? tags-in-result
                       (:name d/tag)))
        (is (contains? tags-in-result
                       (:name d/snapshot-tag)))
        (is (contains? tags-in-result
                       "latest"))
        ; check that they are associated with correct image
        (let [snapshot (first (filter #(= (:name d/snapshot-tag) (first %)) result))
              latest (first (filter #(= "latest" (first %)) result))
              real (first (filter #(= (:name d/tag) (first %)) result))]
          (is (= (second snapshot)
                 (:id alternative)))
          (is (= (second real)
                 (:id root)))
          (is (= (second snapshot)
                 (second latest)))))

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
    (component/stop system))))
