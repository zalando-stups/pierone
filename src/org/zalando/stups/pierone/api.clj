(ns org.zalando.stups.pierone.api
  (:require [org.zalando.stups.friboo.system.http :refer [def-http-component]]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.ring :as fring]
            [org.zalando.stups.pierone.sql :as sql]
            [ring.util.response :as ring]
            [environ.core :refer [env]]
            [org.zalando.stups.pierone.api-v2]
            [org.zalando.stups.pierone.api-v1]))

(def api-definition-suffix
  (or (:http-api-definition-suffix env) ""))

; define the API component and its dependencies
(def-http-component API (str "api/pierone-api" api-definition-suffix ".yaml") [db storage])

(def default-http-configuration
  {:http-port 8080})

(defn read-teams
  "Lists all teams that have artifacts."
  [_ _ db _]
  (let [result (map :team (sql/cmd-list-teams {} {:connection db}))]
    (-> (ring/response result)
        (fring/content-type-json))))

(defn read-artifacts
  "Lists all artifacts of a team."
  [parameters _ db _]
  (let [result (map :artifact (sql/cmd-list-artifacts parameters {:connection db}))]
    (-> (ring/response result)
        (fring/content-type-json))))

(defn read-tags
  "Lists all tags of an artifact."
  [parameters _ db _]
  (let [result (sql/cmd-list-tags parameters {:connection db})]
    (if (seq result)
        ; issue #20
        ; this check is sufficient because an artifact cannot exist without a tag.
        ; if we have no results, then either team or artifact do not exist
        (-> (ring/response result)
            (fring/content-type-json))
        (ring/not-found nil))))

(defn get-scm-source
  "Get SCM source information"
  [parameters _ db _]
  (let [result (first (sql/cmd-get-scm-source parameters {:connection db}))]
    (-> (ring/response result)
        (ring/status (if result 200 404))
        (fring/content-type-json))))

(defn list-tags-for-image
  "Returns tags that point to this image"
  [parameters _ db _]
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
  [team image-sizes db]
  (let [conn {:connection db}
        artifacts (map :artifact
                       (sql/cmd-list-artifacts {:team team} conn))
        tags (reduce #(into %1 (sql/cmd-list-tags {:team team :artifact %2} conn))
                     []
                     artifacts)
        time1 (now)
        used-image-ids (reduce #(into %1
                                     (map :id
                                          (sql/cmd-get-image-ancestry %2 conn)))
                               #{}
                               tags)
        time2 (now)
        storage-fn (fn [sum id]
                     (let [size (get image-sizes id)]
                       (if (number? size)
                           (+ sum size)
                           sum)))
        storage (reduce storage-fn
                        0
                        used-image-ids)]
    (do
      (log/info (str "Fetching image ancestries of " (count tags) " tags took " (- time2 time1) " ms"))
      {:images (count used-image-ids)
       :storage storage
       :artifacts (count artifacts)
       :tags (count tags)})))

(defn get-stats-per-team
  "Returns statistics for a single team"
  [{:keys [team]} _ db _]
  (let [conn {:connection db}
        images (reduce #(assoc %1 (:id %2) (:size %2))
                       {}
                       (sql/cmd-list-images nil conn))
        result (load-stats team images db)]
    (-> result
        (ring/response)
        (fring/content-type-json))))

(defn get-stats
  "Returns statistics for all teams that have an artifact"
  [_ _ db _]
  (let [conn {:connection db}
        teams (map :team (sql/cmd-list-teams nil conn))
        images (reduce #(assoc %1 (:id %2) (:size %2))
                       {}
                       (sql/cmd-list-images nil conn))
        result (map #(assoc {} :team %
                               :stats (load-stats %
                                                  images
                                                  db))
                    teams)]
    (-> result
        (ring/response)
        (fring/content-type-json))))
    