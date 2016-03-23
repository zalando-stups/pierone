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
        manifest-file (slurp "test/org/zalando/stups/pierone/manifest.json")
        manifest (json/parse-string manifest-file true)
        data (.getBytes "imgdata")
        ; echo -n 'imgdata' | sha256sum
        ;digest sha256:a5c741c7dea3a96944022b4b9a0b1480cfbeef5f4cc934850e8afacb48e18c5e
        digest (-> manifest :fsLayers first :blobSum)
        invalid-manifest (.getBytes "stuff")
        ;manifest (str "{\"fsLayers\":[{\"blobSum\":\"" digest "\"}]}")
        pretty-manifest (json/generate-string manifest {:pretty true})
        manifest-bytes (.getBytes pretty-manifest)
        ; manifest2 is simply a different manifest (we use the same FS layer twice, does not make sense, but works)
        manifest2 (update-in manifest [:fsLayers] (fn [old] (vec (take 2 (repeat (first old))))))
        pretty-manifest2 (json/generate-string manifest2 {:pretty true})
        manifest2-bytes (.getBytes pretty-manifest2)]

    (u/wipe-db system)

    ; v2 compatibility check
    (let [result (client/get (u/v2-url "/")
                             (u/http-opts))]
      (is (= 200 (:status result))))

    (expect 404
            (client/head (u/v2-url "/myteam/myart/blobs/" digest)
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
                        (merge (u/http-opts) {:query-params {"digest" digest}})))

    (expect 200
            (client/head (u/v2-url "/myteam/myart/blobs/" digest)
                         (u/http-opts)))

    (expect 404
            (client/get (u/v2-url "/myteam/myart/manifests/1.0")
                        (u/http-opts)))

    (expect 400
            (client/put (u/v2-url "/myteam/myart/manifests/1.0")
                        (u/http-opts (io/input-stream invalid-manifest))))

    (expect 200
            (client/put (u/v2-url "/myteam/myart/manifests/1.0")
                        (u/http-opts (io/input-stream manifest-bytes))))

    (is (= pretty-manifest
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
            (client/get (u/v1-url "/images/" digest "/json")
                        (u/http-opts)))

    ; check that *-SNAPSHOT tags are mutable
    (expect 200
            (client/put (u/v2-url "/myteam/myart/manifests/1.0-SNAPSHOT")
                        (u/http-opts (io/input-stream manifest-bytes))))

    ; works, no changes
    (expect 200
            (client/put (u/v2-url "/myteam/myart/manifests/1.0-SNAPSHOT")
                        (u/http-opts (io/input-stream manifest-bytes))))

    (is (= pretty-manifest
           (expect 200
                   (client/get (u/v2-url "/myteam/myart/manifests/1.0-SNAPSHOT")
                               (u/http-opts)))))

    ; update, new manifest
    (expect 200
            (client/put (u/v2-url "/myteam/myart/manifests/1.0-SNAPSHOT")
                        (u/http-opts (io/input-stream manifest2-bytes))))

    (is (= pretty-manifest2
           (expect 200
                   (client/get (u/v2-url "/myteam/myart/manifests/1.0-SNAPSHOT")
                               (u/http-opts)))))

    ; stop
    (component/stop system)))
