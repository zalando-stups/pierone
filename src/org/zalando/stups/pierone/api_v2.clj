(ns org.zalando.stups.pierone.api-v2
  (:require [org.zalando.stups.friboo.system.http :refer [def-http-component resolve-access-token]]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.user :as u]
            [clojure.data.json :as json]
            [ring.util.response :as ring]
            [org.zalando.stups.pierone.sql :as sql]
            [clojure.java.io :as io]
            [org.zalando.stups.friboo.ring :refer :all]
            [com.netflix.hystrix.core :refer [defcommand]]
            [org.zalando.stups.pierone.storage :as s])
  (:import (java.sql SQLException)
           (java.util UUID)
           (java.io FileInputStream File)))

(defn require-write-access
  "Require write access to team repository"
  [team request]
  ; either user has write access to all (service user)
  ; or user belongs to a team (employee user)
  (when-not (get-in request [:tokeninfo "application.write_all"])
    (u/require-internal-team team request)))

;; Docker Registry API v2
;;
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

(defn ping
  "Checks for compatibility with version 2."
  [_ request _ _]
  ; NOTE: we are doing our own OAuth check here as Docker requires an extra V2 header set
  (let [tokeninfo-url (:tokeninfo-url (:configuration request))]
       (if tokeninfo-url
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

(defn patch-upload
  "Upload FS layer blob"
  [{:keys [team artifact uuid data]} request db storage]
  (require-write-access team request)
  (let [^File upload-file (get-upload-file storage team artifact uuid)]
       (io/copy data upload-file)
       (-> (ring/response "")
           (ring/status 202)
           (ring/header "Docker-Upload-UUID" uuid)
           ; is Docker really expecting an invalid Range header here?
           (ring/header "Range" (str "0-" (- (.length upload-file) 1))))))

(defn put-upload
  "Commit FS layer blob"
  [{:keys [team artifact uuid digest data]} request db storage]
  (require-write-access team request)
  (let [^File upload-file (get-upload-file storage team artifact uuid)
              size        (.length upload-file)
        image-ident (str team "/" artifact "/" digest)]
       (when (zero? size)
             (io/copy data upload-file))
       (store-image storage digest upload-file)
       (sql/create-image!
        {:image    digest
         :metadata ""
         :parent   nil
         :user     (get-in request [:tokeninfo "uid"])}
        {:connection db})
       (sql/accept-image! {:image digest :size size} {:connection db})
       (log/info "Stored new image %s." image-ident)
       (io/delete-file upload-file true)
       (-> (ring/response "")
           (ring/status 201))))

(defn head-blob
  "Check whether image (FS layer) exists."
  [{:keys [digest]} request db _]
  (if (seq (sql/get-image-metadata {:image digest} {:connection db}))
    (resp "OK" request)
    (resp "image not found" request :status 404)))

(defn get-blob
  "Reads the binary data of an image."
  [{:keys [digest]} request _ storage]
  (if-let [data (load-image storage digest)]
    (resp data request :binary? true)
    (resp "image not found" request :status 404)))

(defn put-manifest
  "Stores an image's JSON metadata. Last call in upload sequence."
  [{:keys [team artifact name data]} request db _]
  (require-write-access team request)
  (let [metadata (json/read-str (slurp data))
        connection {:connection db}
        uid (get-in request [:tokeninfo "uid"])
        ; TODO
        digest (get (first (get metadata "fsLayers")) "blobSum")
        params-with-user {:team team
                          :artifact artifact
                          :name name
                          :image digest
                          :manifest (json/write-str metadata)
                          :user uid}
        tag-ident (str team "/" artifact ":" name)]
    (if (= "latest" name)
      (resp "tag latest is not allowed" request :status 409)
      (try
        (sql/create-manifest! params-with-user connection)
        (log/info "Stored new tag %s." tag-ident)
        (resp "OK" request)

        ; TODO check for hystrix exception and replace sql above with cmd- version
        (catch SQLException e
          (if (.endsWith name "-SNAPSHOT")
            (let [updated-rows (sql/update-manifest! params-with-user connection)]
              (if (pos? updated-rows)
                (do
                  (log/info "Updated snapshot tag %s." tag-ident)
                  (resp "OK" request))
                (do
                  (log/info "Did not update snapshot tag %s because image is the same." tag-ident)
                  (resp "tag not modified" request))))
            (do
              (log/warn "Prevented update of tag %s: %s" tag-ident (str e))
              (resp {:errors [{:code "TAG_INVALID" :message "tag already exists"}]} request :status 409))))))))

(defn get-manifest
  "get"
  [parameters request db _]
  (if-let [manifest (:manifest (first (sql/get-manifest parameters {:connection db})))]
    (resp manifest request)
    (resp "manifest not found" request :status 404)))

