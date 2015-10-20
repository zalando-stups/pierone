(ns org.zalando.stups.pierone.api-v2
  (:require [org.zalando.stups.friboo.system.http :refer [def-http-component]]
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

(defn ping
  "Checks for compatibility with version 2."
  [_ _ _ _]
  (-> (ring/response "OK")
      (ring/header "Docker-Distribution-API-Version" "registry/2.0")))

(defn post-upload
  ""
  [{:keys [team artifact]} request db _]
  (require-write-access team request)
  (let [uuid (UUID/randomUUID)]
       (-> (ring/response "")
           (ring/status 202)
           (ring/header "Location" (str "/v2/" team "/" artifact "/blobs/uploads/" uuid))
           (ring/header "Docker-Upload-UUID" uuid))))

(defn patch-upload
  ""
  [{:keys [team artifact uuid data]} request db storage]
  (require-write-access team request)
  (let [^File upload-file (io/file (:directory storage)
                                   (str "upload-" uuid))]
       (io/copy data upload-file)
       (-> (ring/response "")
           (ring/status 202)
           (ring/header "Docker-Upload-UUID" uuid)
           ; is Docker really expecting an invalid Range header here?
           (ring/header "Range" (str "0-" (- (.length upload-file) 1))))))

(defn put-upload
  ""
  [{:keys [team artifact uuid digest data]} request db storage]
  (require-write-access team request)
  (let [^File upload-file (io/file (:directory storage)
                                   (str "upload-" uuid))]

       (io/copy data upload-file)
       (store-image storage digest upload-file)
       (sql/create-image!
        {:image    digest
         :metadata ""
         :parent   nil
         :user     (get-in request [:tokeninfo "uid"])}
        {:connection db})
       (sql/accept-image! {:image digest :size (.length upload-file)} {:connection db})
       (log/info "Stored new image %s." digest)
       (io/delete-file upload-file true)
       (-> (ring/response "")
           (ring/status 201))))

(defn head-blob
  "Reads the binary data of an image."
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
                          :user uid}]
    (if (= "latest" name)
      (resp "tag latest is not allowed" request :status 409)
      (try
        (sql/create-tag! params-with-user connection)
        (log/info "Stored new tag %s." params-with-user)
        (resp "OK" request)

        ; TODO check for hystrix exception and replace sql above with cmd- version
        (catch SQLException e
          (if (.endsWith name "-SNAPSHOT")
            (let [updated-rows (sql/cmd-update-tag! params-with-user connection)]
              (if (pos? updated-rows)
                (do
                  (log/info "Updated snapshot tag %s." params-with-user)
                  (resp "OK" request))
                (do
                  (log/info "Did not update snapshot tag %s because image is the same." params-with-user)
                  (resp "tag not modified" request))))
            (do
              (log/warn "Prevented update of tag: %s" (str e))
              (resp {:errors [{:code "TAG_INVALID" :message "tag already exists"}]} request :status 409))))))))

(defn get-manifest
  "get"
  [{:keys [team artifact name]} request db _]
  ; TODO
  nil)

