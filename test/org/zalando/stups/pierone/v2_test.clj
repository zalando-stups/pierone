(ns org.zalando.stups.pierone.v2-test
  (:require [org.zalando.stups.pierone.test-data :as d]
            [org.zalando.stups.pierone.test-utils :as u]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :refer :all :as json]
            [com.stuartsierra.component :as component]))

(defn expect [status-code response]
  (is (= (:status response)
         status-code)
      (apply str "response of wrong status: " response))
  (:body response))

(deftest v2-test
  (let [system (u/setup)
        data (.getBytes "imgdata")]
    (u/wipe-db system)

    ; v2 compatibility check
    (let [result (client/get (u/v2-url "/")
                             (u/http-opts))]
      (is (= 200 (:status result))))

    (expect 404
            (client/head (u/v2-url "/myteam/myart/blobs/" (:digest d/manifest-v1))
                         (u/http-opts)))

    (expect 202
            (client/post (u/v2-url "/myteam/myart/blobs/uploads/")
                         (u/http-opts)))

    (expect 202
            (client/patch (u/v2-url "/myteam/myart/blobs/uploads/myuuid")
                          (u/http-opts (io/input-stream data))))

    (expect 400
            ; missing "digest" parameter
            (client/put (u/v2-url "/myteam/myart/blobs/uploads/myuuid")
                        (u/http-opts)))

    (expect 404
            ; wrong "digest" parameter
            (client/put (u/v2-url "/myteam/myart/blobs/uploads/myuuid")
                        (merge (u/http-opts) {:query-params {"digest" "wrongdigest"}})))

    (expect 201
            ; correct "digest" parameter
            (client/put (u/v2-url "/myteam/myart/blobs/uploads/myuuid")
                        (merge (u/http-opts) {:query-params {"digest" (:digest d/manifest-v1)}})))

    (expect 200
            (client/head (u/v2-url "/myteam/myart/blobs/" (:digest d/manifest-v1))
                         (u/http-opts)))

    (expect 404
            (client/get (u/v2-url "/myteam/myart/manifests/1.0")
                        (u/http-opts)))

    (expect 400
            ; not json
            (client/put (u/v2-url "/myteam/myart/manifests/1.0")
                        (u/http-opts (io/input-stream (.getBytes "invalid manifest")))))

    (expect 400
            ; invalid schema version
            (client/put (u/v2-url "/myteam/myart/manifests/1.0")
                        (u/http-opts (io/input-stream (:bytes d/manifest-v4)))))

    (expect 201
            (client/put (u/v2-url "/myteam/myart/manifests/1.0")
                        (u/http-opts (io/input-stream (:bytes d/manifest-v1)))))

    (is (= (:pretty d/manifest-v1)
           (expect 200
            (client/get (u/v2-url "/myteam/myart/manifests/1.0")
                        (u/http-opts)))))

    (is (= "{\"name\":\"myteam/myart\",\"tags\":[\"1.0\"]}"
           (expect 200
            (client/get (u/v2-url "/myteam/myart/tags/list")
                        (u/http-opts)))))

    (is (= "{\"repositories\":[\"myteam/myart\"]}"
           (expect 200
            (client/get (u/v2-url "/_catalog")
                        (u/http-opts)))))

    ; check that v1 API returns 404 for v2 images
    (expect 404
            (client/get (u/v1-url "/repositories/myteam/myart/tags/1.0")
                        (u/http-opts)))

    (expect 404
            (client/get (u/v1-url "/images/" (:digest d/manifest-v1) "/json")
                        (u/http-opts)))

    ; check that *-SNAPSHOT tags are mutable
    (expect 201
            (client/put (u/v2-url "/myteam/myart/manifests/1.0-SNAPSHOT")
                        (u/http-opts (io/input-stream (:bytes d/manifest-v1)))))

    ; works, no changes
    (expect 201
            (client/put (u/v2-url "/myteam/myart/manifests/1.0-SNAPSHOT")
                        (u/http-opts (io/input-stream (:bytes d/manifest-v1)))))

    (let [response (client/get (u/v2-url "/myteam/myart/manifests/1.0-SNAPSHOT")
                                (u/http-opts))]
      (is (= "application/vnd.docker.distribution.manifest.v1+prettyjws"
          (get-in response [:headers "Content-Type"]))))

    ; update, new manifest
    (expect 201
            (client/put (u/v2-url "/myteam/myart/manifests/1.0-SNAPSHOT")
                        (u/http-opts (io/input-stream (:bytes d/manifest-v1-multilayer)))))

    (is (= (:pretty d/manifest-v1-multilayer)
           (expect 200
            (client/get (u/v2-url "/myteam/myart/manifests/1.0-SNAPSHOT")
                        (u/http-opts)))))

    (expect 201 (client/put (u/v2-url "/myteam/myart/manifests/2.0-SNAPSHOT")
                                (u/http-opts (io/input-stream (:bytes d/manifest-v2)))))

    (let [response (client/get (u/v2-url "/myteam/myart/manifests/2.0-SNAPSHOT")
                                (u/http-opts))]

      (is (= "application/vnd.docker.distribution.manifest.v2+json"
             (get-in response [:headers "Content-Type"]))))


    ; stop
    (component/stop system)))
