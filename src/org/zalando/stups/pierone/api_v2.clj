(ns org.zalando.stups.pierone.api-v2
  (:require [org.zalando.stups.friboo.system.http :refer [def-http-component]]
            [org.zalando.stups.friboo.system.oauth2 :refer [resolve-access-token]]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.user :as u]
            [io.sarnowski.swagger1st.util.api :as api]
            [org.zalando.stups.pierone.api-v1 :as v1 :refer [require-write-access]]
            [cheshire.core :as json]
            [digest]
            [ring.util.response :as ring]
            [org.zalando.stups.pierone.sql :as sql]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [org.zalando.stups.friboo.ring :refer :all]
            [com.netflix.hystrix.core :refer [defcommand]]
            [org.zalando.stups.pierone.storage :as s])
  (:import (java.sql SQLException)
           (java.util UUID)
           (java.io FileInputStream File)
           (java.security MessageDigest)))

;; Docker Registry API v2
;;
(def errors { :MANIFEST_UNKNOWN {:code "MANIFEST_UNKNOWN" :message "manifest unknown" :detail {}}
              :BLOB_UNKNOWN     {:code "BLOB_UNKNOWN" :message "blob unknown to registry" :detail {}}
              :TAG_INVALID      {:code "TAG_INVALID" :message "tag already exists" :detail {}}})

;{:errors [(assoc-in (:BLOB_UNKNOWN errors) [:detail] {"Digest" digest})]}
(defn get-error-response [error-id args]
  (condp = error-id
    :BLOB_UNKNOWN {:errors [(assoc (:BLOB_UNKNOWN errors) :detail args)]}
    :MANIFEST_UNKNOWN {:errors [(assoc (:MANIFEST_UNKNOWN errors) :detail args)]}
    :TAG_INVALID {:errors [(assoc (:TAG_INVALID errors) :detail args)]}
    {}))

(def json-pretty-printer (json/create-pretty-printer
                            { :indentation                  3
                              :object-field-value-separator ": "
                              :indent-arrays?               true
                              :indent-objects?              true}))
(defn- resp
  "Returns a response including various Docker headers set."
  [body request & {:keys [status binary?]
                   :or   {status  200
                          binary? false}}]
  (let [content-type-fn (fn [response]
                          (if binary?
                            (ring/content-type response "application/octet-stream")
                            (ring/content-type response "application/json")))]
    (-> (ring/response body)
        (content-type-fn)
        (ring/status status))))

(defcommand store-image
            [storage image tmp-file]
            (s/write-data storage image tmp-file))

(defcommand load-image
            [storage image]
            (s/read-data storage image))

(defn ping-unauthorized []
    (-> (ring/response "Unauthorized")
        (ring/status 401)
        (ring/header "WWW-Authenticate" "Basic realm=\"Pier One Docker Registry\"")
        ; IMPORTANT: we need to set the V2 header here (even for 401 status code!),
        ; otherwise the Docker client will fallback to V1
        (ring/header "Docker-Distribution-API-Version" "registry/2.0")))

(defn ping-ok []
    (-> (ring/response "OK")
        (ring/header "Docker-Distribution-API-Version" "registry/2.0")))

; TODO: this function was copied from Swagger1st library
; because the function is not public there :-(
(defn extract-access-token
  "Extracts the bearer token from the Authorization header."
  [request]
  (if-let [authorization (get-in request [:headers "authorization"])]
    (when (.startsWith authorization "Bearer ")
      (.substring authorization (count "Bearer ")))))

(defn is-write-domain
  "Check whether the current 'Host' (domain) is to push images.
  This is a workaround for the misbehaving Docker client (see https://github.com/zalando-stups/pierone/issues/57)."
  [request]
  (if-let [host (get-in request [:headers "host"])]
    (.contains host "write")
    (false)))

(defn ping
  "Checks for compatibility with version 2."
  [_ request _ _]
  ; NOTE: we are doing our own OAuth check here as Docker requires an extra V2 header set
  (let [config (:configuration request)
        tokeninfo-url (:tokeninfo-url config)
        allow-public-read (:allow-public-read config)]
       (if (and tokeninfo-url (or (not allow-public-read) (is-write-domain request)))
           (if-let [access-token (extract-access-token request)]
                   ; check access token
                   (if-let [tokeninfo (resolve-access-token tokeninfo-url access-token)]
                           (ping-ok)
                           (ping-unauthorized))
                   ; missing access token
                   (ping-unauthorized))
           ; no tokeninfo URL => no security checks
           (ping-ok))))

(defn post-upload
  ""
  [{:keys [team artifact]} request db _]
  (require-write-access team request)
  (let [uuid (UUID/randomUUID)]
       (-> (ring/response "")
           (ring/status 202)
           (ring/header "Location" (str "/v2/" team "/" artifact "/blobs/uploads/" uuid))
           (ring/header "Docker-Upload-UUID" uuid))))

(defn get-upload-file [storage team artifact uuid]
  (let [^File dir (io/file (:directory storage) "uploads" team artifact)]
    (.mkdirs dir)
    (io/file dir (str uuid ".upload-blob"))))

(defn get-download-file [storage digest]
  (let [^File dir (io/file (:directory storage) "downloads")]
    (.mkdirs dir)
    (io/file dir (str digest ".download-blob"))))

(defn hexify [digest]
  (apply str (map #(format "%02x" (bit-and % 0xff)) digest)))

(defn compute-digest [file]
  (let [algorithm "SHA-256"
        prefix (.replaceAll (.toLowerCase algorithm) "-" "")
        bufsize 16384
        md (MessageDigest/getInstance algorithm)]
       (with-open [in (io/input-stream file)]
                  (let [ba (byte-array bufsize)]
                  (loop [n (.read in ba 0 bufsize)]
                        (when (> n 0)
                              (.update md ba 0 n)
                              (recur (.read in ba 0 bufsize))))))
       (str prefix ":" (hexify (.digest md)))))

(defn create-image [team artifact digest upload-file request db storage]
  (let [image-ident (str team "/" artifact "/" digest)]
       (try
            (sql/create-image-blob!
             {:image    digest
              :size     (.length upload-file)
              :user     (get-in request [:tokeninfo "uid"])}
             {:connection db})
            (catch SQLException e
                   (if (seq (sql/image-blob-exists {:image digest} {:connection db}))
                       (log/info "Image already exists: %s" image-ident)
                       (throw e))))
       (store-image storage digest upload-file)
       (when-let [scm-source (v1/get-scm-source-data upload-file)]
                 (log/info "Found scm-source.json in image %s: %s" image-ident scm-source)
                 (try
                      (sql/create-scm-source-data! (assoc scm-source :image digest) {:connection db})
                      (catch SQLException e
                             (when-not (seq (sql/image-blob-exists {:image digest} {:connection db}))
                                       (throw e)))))
       (log/info "Stored new image %s." image-ident)))

(defn patch-upload
  "Upload FS layer blob"
  [{:keys [team artifact uuid data]} request db storage]
  (require-write-access team request)
  (let [^File upload-file (get-upload-file storage team artifact uuid)]
       (io/copy data upload-file)
       (let [digest (compute-digest upload-file)
             size   (.length upload-file)]
            (create-image team artifact digest upload-file request db storage)
            (io/delete-file upload-file true)
            (-> (ring/response "")
                (ring/status 202)
                (ring/header "Docker-Upload-UUID" uuid)
                ; is Docker really expecting an invalid Range header here?
                (ring/header "Range" (str "0-" (- size 1)))))))

(defn put-upload
  "Commit FS layer blob"
  [{:keys [team artifact uuid digest data]} request db storage]
  (require-write-access team request)
  (let [^File upload-file (get-upload-file storage team artifact uuid)
        image-ident (str team "/" artifact "/" digest)]
       ; TODO: file might be uploaded on PUT too

       (let [updated-rows (sql/accept-image-blob! {:image digest} {:connection db})]
            (if (pos? updated-rows)
                (do
                  (log/info "Accepted image %s." image-ident)
                  (-> (ring/response "")
                      (ring/status 201)))
                (do
                  (resp (get-error-response :BLOB_UNKNOWN {"Digest" digest}) request :status 404))))))

(defn head-blob
  "Check whether image (FS layer) exists."
  [{:keys [digest]} request db _]
  (if-let [size (:size (first (sql/get-blob-size {:image digest} {:connection db})))]
    (-> (resp "OK" request)
        (ring/header "Docker-Content-Digest" digest)
        (ring/header "Content-Length" size))
    (resp "image not found" request :status 404)))

(defn get-blob
  "Reads the binary data of an image."
  [{:keys [digest]} request db storage]
  (if-let [size (:size (first (sql/get-blob-size {:image digest} {:connection db})))]
          (let [data (load-image storage digest)]
               (-> (resp data request :binary? true)
                   (ring/header "Docker-Content-Digest" digest)
                   (ring/header "Content-Length" size)
                   ; layers are already GZIP compressed!
                   (ring/header "Content-Encoding" "identity")))
    (resp (get-error-response :BLOB_UNKNOWN {"Digest" digest}) request :status 404)))

(defn read-manifest
  "Read manifest JSON and throw 'Bad Request' if invalid"
  [data]
  (if (map? data)
    data
    (try
      (walk/keywordize-keys (json/parse-string (slurp data)))
      (catch Exception e
        (api/throw-error 400 (str data))))))

(defn get-fs-layers
   [manifest]
   (let [manifest-version (get manifest :schemaVersion)]
   (if (= 1 manifest-version)
      (map :blobSum (:fsLayers manifest ))
      (if (= 2 manifest-version)
          (apply conj [(get-in manifest [:config :digest])]
                 (map :digest (:layers manifest)))
          (api/throw-error 400 (str "manifest version not compatible with this API: " manifest-version))))))

(defn put-manifest
  "Stores an image's JSON metadata. Last call in upload sequence."
  [{:keys [team artifact name data]} request db _]
  (require-write-access team request)
  (let [manifest (read-manifest data)
        connection {:connection db}
        uid (get-in request [:tokeninfo "uid"])
        fs-layers (get-fs-layers manifest)
        digest (first fs-layers)
        params-with-user {:team team
                          :artifact artifact
                          :name name
                          :image digest
                          :manifest (json/generate-string manifest {:pretty true})
                          ;:manifest (json/generate-string manifest)
                          :fs_layers fs-layers
                          :user uid}
        tag-ident (str team "/" artifact ":" name)]
    (when-not (seq fs-layers)
              (api/throw-error 400 "manifest has no FS layers"))
    (doseq [digest fs-layers]
          (when-not (seq (sql/image-blob-exists {:image digest} {:connection db}))
                    (api/throw-error 400 (str "image blob " digest " does not exist"))))
    (if (= "latest" name)
      (resp "tag latest is not allowed" request :status 409)
      (try
        (sql/create-manifest! params-with-user connection)
        (log/info "Stored new tag %s." tag-ident)
        (-> (resp "OK" request :status 201)
            (ring/header "Docker-Content-Digest"
                         (str "sha256:"
                              (-> data
                                  (json/encode {:pretty json-pretty-printer})
                                  (digest/sha-256)))))

        ; TODO check for hystrix exception and replace sql above with cmd- version
        (catch SQLException e
          (if (.endsWith name "-SNAPSHOT")
            (let [updated-rows (sql/update-manifest! params-with-user connection)]
              (if (pos? updated-rows)
                (do
                  (log/info "Updated snapshot tag %s." tag-ident)
                  (resp "OK" request :status 201))
                (do
                  (log/info "Did not update snapshot tag %s because image is the same." tag-ident)
                  (resp "tag not modified" request :status 201))))
            (do
              (log/warn "Prevented update of tag %s: %s" tag-ident (str e))
              (resp (get-error-response :TAG_INVALID {"Tag" name} request :status 409)))))))))

(defn get-manifest
  "get"
  [parameters request db _]
  (if-let [manifest (:manifest (first (sql/get-manifest parameters {:connection db})))]
    (let [parsed-manifest (json/decode manifest)
          pretty (json/encode parsed-manifest {:pretty json-pretty-printer})
          schemaVersion (get parsed-manifest "schemaVersion")
          set-header-fn (fn [response]
                          (condp = schemaVersion
                            1 (ring/content-type response "application/vnd.docker.distribution.manifest.v1+prettyjws")
                            2 (ring/content-type response "application/vnd.docker.distribution.manifest.v2+json")
                            response))]
      (-> (resp pretty request)
          (set-header-fn)
          (ring/header "Docker-Content-Digest" (str "sha256:" (digest/sha-256 pretty)))))
      (resp (get-error-response :MANIFEST_UNKNOWN {"Parameters" parameters}) request :status 404)))

(defn list-tags
  "get"
  [{:keys [team artifact] :as parameters} request db _]
  (let [tags (map :name (sql/list-tag-names parameters {:connection db}))]
    (resp {:name (str team "/" artifact) :tags tags} request)))

(defn list-repositories
  "get"
  [_ request db _]
  (let [repos (map :name (sql/list-repositories {} {:connection db}))]
    (resp {:repositories repos} request)))
