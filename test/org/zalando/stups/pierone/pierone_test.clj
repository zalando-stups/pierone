(ns org.zalando.stups.pierone.pierone-test
  (:require [org.zalando.stups.pierone.test-data :as d]
            [org.zalando.stups.pierone.test-utils :as u]
            [clojure.test :refer :all]
            [cheshire.core :as json]
            [clj-http.client :as client]
            [com.stuartsierra.component :as component]))

(deftest pierone-test
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

    ; stop
    (component/stop system)))
