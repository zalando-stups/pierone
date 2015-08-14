(ns org.zalando.stups.pierone.pierone-test
  (:require [org.zalando.stups.pierone.test-data :as d]
            [org.zalando.stups.pierone.test-utils :as u]
            [clojure.test :refer :all]
            [clojure.data.json :as json]
            [clj-http.client :as client]
            [com.stuartsierra.component :as component]))

(deftest pierone-test
  (let [system (u/setup d/all-tags
                        d/all-images)
        root (first d/images-hierarchy)
        alt (second d/images-hierarchy)]

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
                     (json/read-str :key-fn keyword)
                     (first))]
        (is (= (:artifact result)
               (:artifact d/tag)))
        (is (= (:team result)
               (:team d/tag)))
        (is (= (:name result)
               (:name d/tag))))
    
    (is (= 404 (:status (client/get (u/p1-url "/tags/asdfa")
                                    (u/http-opts)))))
    (is (= 412 (:status (client/get (u/p1-url "/tags/img")
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

    ; stop
    (component/stop system)))
      