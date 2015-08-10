(ns org.zalando.stups.pierone.api
  (:require [org.zalando.stups.friboo.system.http :refer [def-http-component]]
            [org.zalando.stups.pierone.sql :as sql]
            [ring.util.response :as ring]
            [org.zalando.stups.friboo.ring :as fring]
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
        (if (> 1 (count images))
            ; more than one image matched
            (ring/status (ring/response nil)
                         412)
            ; exactly one image matched
            (-> (sql/cmd-list-tags-for-image parameters conn)
                (ring/response)
                (fring/content-type-json)))
        ;empty
        (ring/not-found nil))))
