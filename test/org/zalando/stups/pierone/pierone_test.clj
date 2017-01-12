(ns org.zalando.stups.pierone.pierone-test
  (:require [org.zalando.stups.pierone.test-data :as d]
            [org.zalando.stups.pierone.test-utils :as u]
            [org.zalando.stups.pierone.api :refer :all]
            [org.zalando.stups.pierone.sql :as sql]
            [clojure.test :refer :all]
            [cheshire.core :as json]
            [clj-http.client :as client]
            [com.stuartsierra.component :as component]
            [midje.sweet :refer :all]
            [org.zalando.stups.pierone.clair :as clair]
            [clojure.java.io :as io]))

(deftest ^:unit wrap-midje-facts

  (facts "get-clair-link"
    (get-clair-link {} "foo") => nil
    (get-clair-link {:clair_id "sha256:42"} "") => nil
    (get-clair-link {:clair_id "sha256:42"} "https://foo") => "https://foo/v1/layers/sha256:42"
    (get-clair-link {:clair_id "sha256:42"} "https://foo/") => "https://foo/v1/layers/sha256:42")

  (facts "read-tags"

    (fact "When clair-url is set, generates a link to clair"
      (read-tags ..parameters.. nil ..db.. nil {:clair-url "https://clair.example.com"} ..logger..)
      => (contains {:status 200 :body [{:clair_id      "sha256:42"
                                        :clair_details "https://clair.example.com/v1/layers/sha256:42"}]})
      (provided
        (sql/cmd-list-tags ..parameters.. {:connection ..db..}) => [{:clair_id "sha256:42"}]))

    (fact "When clair-url is not set, generates nil"
      (read-tags ..parameters.. nil ..db.. nil nil ..logger..)
      => (contains {:status 200 :body [{:clair_id      "sha256:42"
                                        :clair_details nil}]})
      (provided
        (sql/cmd-list-tags ..parameters.. {:connection ..db..}) => [{:clair_id "sha256:42"}])))

  )

(deftest ^:integration pierone-test
  (with-redefs [org.zalando.stups.friboo.system.oauth2/map->OAUth2TokenRefresher u/map->NoTokenRefresher
                clair/send-sqs-message (fn [& _])]
    (let [system (u/setup)
          root (first d/images-hierarchy)
          alt (second d/images-hierarchy)]

      (u/wipe-db system)
      (u/push-images d/images-hierarchy)

      ; tag root image as regular tag
      (u/expect 200 (client/put (u/v1-url "/repositories/" (:team d/tag)
                                  "/" (:artifact d/tag)
                                  "/tags/" (:name d/tag))
                      (u/http-opts (u/wrap-quotes (:id root))
                        :json)))
      ; tag alt image as snapshot tag
      (u/expect 200 (client/put (u/v1-url "/repositories/" (:team d/snapshot-tag)
                                  "/" (:artifact d/snapshot-tag)
                                  "/tags/" (:name d/snapshot-tag))
                      (u/http-opts (u/wrap-quotes (:id alt))
                        :json)))

      ; reverse image search
      (is (= 200
            (:status (client/get (u/p1-url "/tags/" (:id root))))))

      (let [result (-> (client/get (u/p1-url "/tags/" (:id root)))
                     (:body)
                     (json/parse-string keyword)
                     (first))]
        (is (= (:artifact result)
              (:artifact d/tag)))
        (is (= (:team result)
              (:team d/tag)))
        (is (= (:name result)
              (:name d/tag))))

      (is (= 404 (:status (client/get (u/p1-url "/tags/asdfa")
                            (u/http-opts)))))
      (is (= 412 (:status (client/get (u/p1-url "/tags/abc")
                            (u/http-opts)))))


      ; check tag list for not existing artifact -> not ok
      (is (= 404 (:status (client/get (u/p1-url "/teams/"
                                        (:team d/tag)
                                        "/artifacts/asdfasdf"
                                        "/tags")
                            (u/http-opts)))))

      (is (= 200 (:status (client/get (u/p1-url "/teams/" (:team d/tag)
                                        "/artifacts/" (:artifact d/tag)
                                        "/tags")
                            (u/http-opts)))))

      ; check stats endpoint
      (let [resp (client/get (u/p1-url "/stats/teams/" (:team d/tag))
                   (u/http-opts))
            stats (json/parse-string (:body resp) keyword)]
        (is (= 200 (:status resp)))
        (println stats)
        ; kio is the only artifact
        (is (= 1
              (:artifacts stats)))
        ; regular tag and snapshot tag
        (is (= 2
              (:tags stats))))

      (let [resp (client/get (u/p1-url "/stats/teams")
                   (u/http-opts))
            stats (json/parse-string (:body resp) keyword)]
        (is (= 200 (:status resp)))
        (is (= 1 (count stats)))
        (println stats)
        (is (:team (first stats))
          (:team d/tag)))

      (let [resp (client/get (u/p1-url "/stats")
                   (u/http-opts))
            stats (json/parse-string (:body resp) keyword)]
        (is (= 200 (:status resp)))
        (println stats)
        (is (= 1 (:teams stats)))
        (is (= 24 (:storage stats))))

      ;; Push a v2 image
      (client/post (u/v2-url "/myteam/myart/blobs/uploads/")
                   (u/http-opts))
      (client/patch (u/v2-url "/myteam/myart/blobs/uploads/myuuid")
                    (u/http-opts (io/input-stream (.getBytes "imgdata"))))
      (client/put (u/v2-url "/myteam/myart/blobs/uploads/myuuid")
                  (merge (u/http-opts) {:query-params {"digest" (:digest d/manifest-v1)}}))
      (client/put (u/v2-url "/myteam/myart/manifests/1.0")
                  (u/http-opts (io/input-stream (:bytes d/manifest-v1))))


      (let [resp (client/post (u/p1-url "/teams/myteam/artifacts/myart/tags/latest/recheck")
                              (merge (u/http-opts)
                                     {:as :json}))]
        (is (= 202 (:status resp)))
        (is (= {:clair-sqs-messages [{:Layer {:Format     "Docker"
                                              :Name       "sha256:e5d6433ddaf1c332d356a026a065c874bc0ef2553650a8134356320679076d7b"
                                              :ParentName nil
                                              :Path       "foobar/v2/myteam/myart/blobs/sha256:a5c741c7dea3a96944022b4b9a0b1480cfbeef5f4cc934850e8afacb48e18c5e"}}]
                :message            "Image resubmitted"}
               (:body resp))))


      ; stop
      (component/stop system))))
