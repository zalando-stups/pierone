(ns org.zalando.stups.pierone.api
  (:require [org.zalando.stups.friboo.system.http :refer [def-http-component]]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.ring :as fring]
            [org.zalando.stups.pierone.sql :as sql]
            [ring.util.response :as ring]
            [environ.core :refer [env]]
            [org.zalando.stups.pierone.api-v2]
            [org.zalando.stups.pierone.api-v1]
            [clojure.string :as str]
            [org.zalando.stups.friboo.ring :as r]
            [org.zalando.stups.pierone.clair :as clair]
            [cheshire.core :as json]))

(def api-definition-suffix
  (or (:http-api-definition-suffix env) ""))

; define the API component and its dependencies
(def-http-component API (str "api/pierone-api" api-definition-suffix ".yaml") [db storage api-config httplogger])

(def default-http-configuration
  {:http-port 8080})

(defn read-teams
  "Lists all teams that have artifacts."
  [_ _ db _ _ _]
  (let [result (map :team (sql/cmd-list-teams {} {:connection db}))]
    (-> (ring/response result)
        (fring/content-type-json))))

(defn read-artifacts
  "Lists all artifacts of a team."
  [parameters _ db _ _ _]
  (let [result (map :artifact (sql/cmd-list-artifacts parameters {:connection db}))]
    (-> (ring/response result)
        (fring/content-type-json))))

(defn get-clair-link [tags-result clair-url]
  (when-not (str/blank? clair-url)
    (when-let [clair-id (:clair_id tags-result)]
      (r/conpath clair-url "/v1/layers/" clair-id))))

(defn assoc-clair-link [tags-result clair-url]
  (assoc tags-result :clair_details (get-clair-link tags-result clair-url)))

(defn read-tags
  "Lists all tags of an artifact."
  [parameters _ db _ {:keys [clair-url]} _]
  (let [result (sql/cmd-list-tags parameters {:connection db})]
    (if (seq result)
        ; issue #20
        ; this check is sufficient because an artifact cannot exist without a tag.
        ; if we have no results, then either team or artifact do not exist
        (->> result
            (map #(assoc-clair-link % clair-url))
            ring/response
            fring/content-type-json)
        (ring/not-found nil))))

(defn get-scm-source
  "Get SCM source information"
  [parameters _ db _ _ _]
  (let [result (first (sql/cmd-get-scm-source parameters {:connection db}))]
    (-> (ring/response result)
        (ring/status (if result 200 404))
        (fring/content-type-json))))

(defn list-tags-for-image
  "Returns tags that point to this image"
  [parameters _ db _ _ _]
  (let [conn {:connection db}
        images (sql/cmd-get-images parameters conn)]
    (if (seq images)
        ; not empty
        (if (> (count images) 1)
            ; more than one image matched
            (ring/status (ring/response nil)
                         412)
            ; exactly one image matched
            (-> (sql/cmd-list-tags-for-image parameters conn)
                (ring/response)
                (fring/content-type-json)))
        ;empty
        (ring/not-found nil))))

(defn now [] (System/currentTimeMillis))

(defn load-stats
  "Loads usage statistics for a single team"
  [team db]
  (let [conn {:connection db}
        artifacts (map :artifact
                       (sql/cmd-list-artifacts {:team team} conn))
        tags (reduce #(into %1 (sql/cmd-list-tags {:team team :artifact %2} conn))
                     []
                     artifacts)]
    {:artifacts (count artifacts)
     :tags (count tags)}))

(defn get-team-stats
  "Returns statistics for a single team"
  [{:keys [team]} _ db _ _ _]
  (let [conn {:connection db}
        result (load-stats team db)]
    (-> result
        (ring/response)
        (fring/content-type-json))))

(defn get-teams-stats
  "Returns statistics for all teams that have an artifact"
  [_ _ db _ _ _]
  (let [conn {:connection db}
        teams (map :team (sql/cmd-list-teams nil conn))
        result (map #(assoc {} :team %
                               :stats (load-stats % db))
                    teams)]
    (-> result
        (ring/response)
        (fring/content-type-json))))

(defn get-overall-stats
  [_ _ db _ _ _]
  (let [conn {:connection db}
        teams (count (sql/cmd-list-teams nil conn))
        storage (:total (first (sql/cmd-get-total-storage nil conn)))
        result {:teams teams
                :storage storage}]
    (-> result
        (ring/response)
        (fring/content-type-json))))

(defn post-recheck!
  "Resubmit an image to Clair for security checking."
  [{:as params :keys [team artifact]} _ db _ api-config _]
  (prn params)
  (let [queue-url (:clair-layer-push-queue-url api-config)
        queue-region (:clair-layer-push-queue-region api-config)]
    (if (some str/blank? [queue-region queue-url])
      (-> {:message "Clair integration not configured, not doing anything"}
          (ring/response)
          (fring/content-type-json))
      (if-let [manifest-str (org.zalando.stups.pierone.api-v2/load-manifest (assoc params :name (:tag params)) db)]
        (let [manifest (json/decode manifest-str keyword)
              clair-hashes (clair/prepare-hashes-for-clair manifest)
              topmost-layer-clair-id (-> clair-hashes last :current :clair-id)
              registry (:callback-url api-config)
              clair-sqs-messages (map (partial clair/create-sqs-message registry team artifact) clair-hashes)]
          (log/info "Resubmitting image to Clair: %s" topmost-layer-clair-id)
          (clair/send-sqs-message queue-region queue-url clair-sqs-messages)
          (-> {:message "Image resubmitted"
               :clair-sqs-messages clair-sqs-messages}
              (ring/response)
              (ring/status 202)
              (fring/content-type-json)))
        (ring/not-found nil)))))
