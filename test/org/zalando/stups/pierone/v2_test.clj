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

(def json-pretty-printer (json/create-pretty-printer
                            { :indentation                  3
                              :object-field-value-separator ": "
                              :indent-arrays?               true
                              :indent-objects?              true}))

(deftest v2-test
  (let [system (u/setup)
        manifest-file (slurp "test/org/zalando/stups/pierone/manifest.json")
        manifest-file-v2 (slurp "test/org/zalando/stups/pierone/manifestv2.json")
        manifest (json/parse-string manifest-file true)
        data (.getBytes "imgdata")
        ; echo -n 'imgdata' | sha256sum
        ;digest sha256:a5c741c7dea3a96944022b4b9a0b1480cfbeef5f4cc934850e8afacb48e18c5e
        digest (-> manifest :fsLayers first :blobSum)
        manifestv2 (-> (json/parse-string manifest-file-v2 true)
                       (assoc-in [:layers 0 :digest] digest)
                       (assoc-in [:config :digest] digest))
        invalid-manifest (.getBytes "stuff")
        ;manifest (str "{\"fsLayers\":[{\"blobSum\":\"" digest "\"}]}")
        pretty-manifest (json/encode manifest {:pretty json-pretty-printer})
        pretty-manifest-v2 (json/encode manifestv2 {:pretty json-pretty-printer})
        manifest-bytes (.getBytes pretty-manifest)
        manifest-v2-bytes (.getBytes pretty-manifest-v2)
        manifest-invalid-version-bytes (.getBytes (json/encode (assoc manifestv2 :schemaVersion "3") {:pretty json-pretty-printer}))
        ; manifest-double is simply a different manifest (we use the same FS layer twice, does not make sense, but works)
        manifest-double (update-in manifest [:fsLayers] (fn [old] (vec (take 2 (repeat (first old))))))
        pretty-manifest-double (json/encode manifest-double {:pretty { :indentation 3
                                                           :object-field-value-separator ": "
                                                           :indent-arrays? true
                                                           :indent-objects? true}})
        manifest-double-bytes (.getBytes pretty-manifest-double)]

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

    (expect 400
            (client/put (u/v2-url "/myteam/myart/manifests/1.0")
                        (u/http-opts (io/input-stream manifest-invalid-version-bytes))))

    (expect 201
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
    (expect 201
            (client/put (u/v2-url "/myteam/myart/manifests/1.0-SNAPSHOT")
                        (u/http-opts (io/input-stream manifest-bytes))))

    ; works, no changes
    (expect 201
            (client/put (u/v2-url "/myteam/myart/manifests/1.0-SNAPSHOT")
                        (u/http-opts (io/input-stream manifest-bytes))))

    (let [response (client/get (u/v2-url "/myteam/myart/manifests/1.0-SNAPSHOT")
                                (u/http-opts))]
      (is (= "application/vnd.docker.distribution.manifest.v1+prettyjws"
          (get-in response [:headers "Content-Type"]))))

    ; update, new manifest
    (expect 201
            (client/put (u/v2-url "/myteam/myart/manifests/1.0-SNAPSHOT")
                        (u/http-opts (io/input-stream manifest-double-bytes))))

    (is (= pretty-manifest-double
           (expect 200
            (client/get (u/v2-url "/myteam/myart/manifests/1.0-SNAPSHOT")
                        (u/http-opts)))))

    (expect 201 (client/put (u/v2-url "/myteam/myart/manifests/2.0-SNAPSHOT")
                                (u/http-opts (io/input-stream manifest-v2-bytes))))

    (let [response (client/get (u/v2-url "/myteam/myart/manifests/2.0-SNAPSHOT")
                                (u/http-opts))]

      (is (= "application/vnd.docker.distribution.manifest.v2+json"
             (get-in response [:headers "Content-Type"]))))


    ; stop
    (component/stop system)))
