(ns org.zalando.stups.pierone.api-v1
  (:require [org.zalando.stups.friboo.system.http :refer [def-http-component]]
            [org.zalando.stups.friboo.log :as log]
            [ring.util.response :as ring]
            [clojure.data.json :as json]
            [org.zalando.stups.pierone.sql :as sql]
            [clojure.java.io :as io]
            [io.sarnowski.swagger1st.util.api :as api]
            [io.sarnowski.swagger1st.util.security :as security]
            [org.zalando.stups.friboo.user :as u]
            [schema.core :as schema]
            [clojure.data.codec.base64 :as b64]
            [com.netflix.hystrix.core :refer [defcommand]]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [org.zalando.stups.pierone.storage :as s])
  (:import (java.sql SQLException)

           (java.util UUID)
           (org.apache.commons.compress.compressors.gzip GzipCompressorInputStream)
           (org.apache.commons.compress.archivers.tar TarArchiveInputStream)
           (java.io FileInputStream File)))

(schema/defschema ScmSourceInformation
  {:url      schema/Str
   :revision schema/Str
   :author   schema/Str
   :status   schema/Str})

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
        (ring/status status)
        (ring/header "X-Docker-Registry-Version" "0.6.3")
        (ring/header "X-Docker-Token" (get (:tokeninfo request) "access_token" "AnonFakeToken"))
        (ring/header "X-Docker-Endpoints" (get-in request [:headers "host"])))))

(defn require-write-access
  "Require write access to team repository"
  [team request]
  ; either user has write access to all (service user)
  ; or user belongs to a team (employee user)
  (when-not (get-in request [:tokeninfo "application.write_all"])
    (u/require-internal-team team request)))

(defn ping
  "Client checks for compatibility."
  [_ request _ _]
  (resp true request))

(defn search
  "Dummy call. Searches for repositories."
  [{:keys [q] :as parameters} request db _]
  (let [repos (sql/search-repos parameters {:connection db})
        num-results (count repos)]
    (resp {:results     repos
           :query       q
           :page        1
           :page_size   num-results
           :num_pages   1
           :num_results num-results} request)))

(defn put-repo
  "Dummy call."
  [{:keys [team]} request _ _]
  (require-write-access team request)
  (resp "OK" request))

(defn get-tags
  "Get a map of all tags for an artifact with its images."
  [parameters request db _]
  (let [tags (sql/read-tags parameters {:connection db})]
    (if (empty? tags)
      (resp {} request :status 404)
      (let [tags (reduce
                   (fn [tags tag]
                     (merge tags {(:name tag)
                                  (:image tag)}))
                   {} tags)]
        (resp tags request)))))

(defn put-tag
  "Stores a tag. Only '*-SNAPSHOT' tags are mutable."
  [parameters request db _]
  (require-write-access (:team parameters) request)
  (let [params-with-user (assoc parameters :user (get-in request [:tokeninfo "uid"]))]
    (try
      (sql/create-tag! params-with-user {:connection db})
      (log/info "Stored new tag %s." params-with-user)
      (resp "OK" request)

      (catch SQLException e
        (if (.endsWith (:name params-with-user) "-SNAPSHOT")
          (do
            (sql/update-tag! params-with-user {:connection db})
            (log/info "Updated snapshot tag %s." params-with-user)
            (resp "OK" request))
          (do
            (log/warn "Prevented update of tag: %s" (str e))
            (resp "tag already exists" request :status 409)))))))

(defn put-images
  "Dummy call. this is the final call from Docker client when pushing an image
   Docker client expects HTTP status code 204 (No Content) instead of 200 here!"
  [_ request _ _]
  (resp "" request :status 204))

(defn get-images
  "Dummy call."
  [_ request _ _]
  (resp [] request))

(defn put-image-json
  "Stores an image's JSON metadata. First call in upload sequence."
  [{:keys [image metadata]} request db _]
  (try
    (sql/delete-unaccepted-image! {:image image} {:connection db})
    (sql/create-image!
      {:image    image
       :metadata (json/write-str metadata)
       :parent   (:parent metadata)
       :user     (get-in request [:tokeninfo "uid"])}
      {:connection db})
    (log/debug "Stored new image metadata %s." image)
    (resp "OK" request)

    (catch SQLException e
      (log/warn "Prevented update of image: %s", (str e))
      (resp "image already exists" request :status 409))))

(defn get-image-json
  "Returns an image's metadata."
  [parameters request db _]
  (let [result (sql/get-image-metadata parameters {:connection db})]
    (if (empty? result)
      (resp "image not found" request :status 404)
      (resp (-> result first :metadata json/read-str) request))))

(defn extract-scm-source
  [file]
  (schema/validate ScmSourceInformation (json/read-str (slurp file) :key-fn keyword)))

(defn get-scm-source-data
  [tmp-file]
  (try
    (let [fis (FileInputStream. tmp-file)
          tar-stream (TarArchiveInputStream. (GzipCompressorInputStream. fis))]
      (loop []
        (when-let [entry (.getNextTarEntry tar-stream)]
          (if (= (.getName entry) "scm-source.json")
            (extract-scm-source tar-stream)
            (recur)))))
    (catch Exception e
      (log/warn "Failed to read image layer: %s" (str e))
      nil)))

(defcommand store-image
            [storage image tmp-file]
            (s/write-data storage image tmp-file))

(defcommand load-image
            [storage image]
            (s/read-data storage image))

(defn put-image-binary
  "Stores an image's binary data. Second call in upload sequence."
  [{:keys [image data]} request db storage]
  (let [^File tmp-file (io/file (:directory storage) (str image ".tmp-" (UUID/randomUUID)))]
    (io/copy data tmp-file)
    (store-image storage image tmp-file)
    (when-let [scm-source (get-scm-source-data tmp-file)]
      (log/info "Found scm-source.json in image %s: %s" image scm-source)
      (sql/create-scm-source-data! (assoc scm-source :image image) {:connection db}))
    (io/delete-file tmp-file true))
  (sql/accept-image! {:image image} {:connection db})
  (log/info "Stored new image %s." image)
  (resp "OK" request))

(defn get-image-binary
  "Reads the binary data of an image."
  [{:keys [image]} request _ storage]
  (if-let [data (load-image storage image)]
    (resp data request :binary? true)
    (resp "image not found" request :status 404)))

(defn put-image-checksum
  "Dummy call."
  [_ request _ _]
  (resp "OK" request))

(defn get-image-ancestry
  "Returns the whole ancestry of an image."
  [{:keys [image]} request db _]
  ; TODO solve recursion in postgresql (http://www.postgresql.org/docs/9.4/static/queries-with.html)
  (let [f (fn [images image]
            (let [result (sql/get-image-parent {:image image} {:connection db})
                  exists? (first result)
                  parent (:parent exists?)]
              (if exists?
                (if parent
                  (recur (conj images image) parent)
                  (conj images image))
                [])))
        ancestry (f [] image)]
    (if (empty? ancestry)
      (resp "image not found" request :status 404)
      (resp ancestry request))))

(defn post-users
  "Special handler for docker client"
  [_ request _ _]
  (resp "Not supported, please use GET /v1/users" request :status 401))

(defn login
  [_ request _ _]
  (resp "Login successful" request))