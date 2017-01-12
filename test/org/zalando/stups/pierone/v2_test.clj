(ns org.zalando.stups.pierone.v2-test
  (:require [org.zalando.stups.pierone.test-data :as d]
            [org.zalando.stups.pierone.test-utils :as u]
            [org.zalando.stups.pierone.sql :as sql]
            [org.zalando.stups.pierone.auth :as auth]
            [org.zalando.stups.pierone.api-v2 :as v2]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [clj-http.client :as client]
            [com.stuartsierra.component :as component]
            [org.zalando.stups.pierone.clair :as clair]
            [org.zalando.stups.friboo.system.oauth2 :as oauth2]
            [clojure.java.jdbc :as jdbc])
  (:import (java.io File)))

(def request {:configuration {:tokeninfo-url "token.info"}
              :tokeninfo {"realm" "/employees"
                          "uid" "tester"}})
(def params {:team "team"
             :artifact "artifact"
             :name "name"
             :digest "digest"
             :uuid "uuid"
             :data "data"})

(deftest ^:unit v2-unit-test
  (facts "calls require-write-access with correct params"
    (fact "put-manifest"
      (v2/put-manifest params request nil nil nil {:log-fn identity}) => truthy
      (provided
        (v2/read-manifest "data") => "manifest"
        (v2/get-fs-layers "manifest") => ["digest"]
        (clair/prepare-hashes-for-clair "manifest") => []
        (jdbc/db-transaction* nil anything) => nil
        (sql/get-scm-source {:team "team" :artifact "artifact" :tag "name"} {:connection nil}) => {}
        (sql/image-blob-exists {:image "digest"} {:connection nil}) => [0 1 2]
        (auth/require-write-access "team" request) => nil))
    (fact "patch-upload"
      (let [file (new File "foo")]
        (v2/patch-upload params request nil nil nil nil) => truthy
        (provided
          (v2/get-upload-file nil "team" "artifact" "uuid") => file
          (io/copy "data" file) => nil
          (v2/compute-digest file) => "digest"
          (v2/create-image "team" "artifact" "digest" file request nil nil) => nil
          (io/delete-file file true) => nil
          (auth/require-write-access "team" request) => nil)))
    (fact "put-upload"
      (v2/put-upload params request nil nil nil nil) => truthy
      (provided
        (sql/accept-image-blob! {:image "digest"} {:connection nil}) => 0
        (auth/require-write-access "team" request) => nil))
    (fact "post-upload"
      (v2/post-upload params request nil nil nil nil) => truthy
      (provided
        (auth/require-write-access "team" request) => nil))))


(defn expect [status-code response]
  (is (= (:status response)
        status-code)
    (apply str "response of wrong status: " response))
  (:body response))

(deftest ^:integration v2-integration-test
  (with-redefs [org.zalando.stups.pierone.clair/send-sqs-message (fn [& _])
                oauth2/map->OAUth2TokenRefresher u/map->NoTokenRefresher]
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

      (expect 409
              ; duplicate tag
              (client/put (u/v2-url "/myteam/myart/manifests/1.0")
                          (u/http-opts (io/input-stream (:bytes d/manifest-v2)))))

      (is (= (:pretty d/manifest-v1)
             (expect 200
                     (client/get (u/v2-url "/myteam/myart/manifests/1.0")
                                 (u/http-opts)))))

      (is (= (:pretty d/manifest-v1)
             (expect 200
                     (client/get (u/v2-url "/myteam/myart/manifests/sha256:b0ebea73273b4d5a334d74cd826a2327f260fe5212613937add8b5e171bf49bd")
                                 (u/http-opts)))))

      (is (= (:pretty d/manifest-v1)
             (expect 200
                     (client/get (u/v2-url "/myteam/myart/manifests/latest")
                                 (u/http-opts)))))

      (is (= (:pretty d/manifest-v1)
             (expect 200
                     (client/get (u/v2-url "/myteam/myart/manifests/sha256:b0ebea73273b4d5a334d74cd826a2327f260fe5212613937add8b5e171bf49bd")
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
      (component/stop system))))
