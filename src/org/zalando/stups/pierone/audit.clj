(ns org.zalando.stups.pierone.audit
  (:require [clj-time.format :as tf]
            [clj-time.core :as t]))

(def date-formatter
  (tf/formatters :date-time-no-ms))

(defn get-date
  []
  (tf/unparse date-formatter (t/now)))

(defn remove-nil
  [data]
  (into {} (remove (comp nil? second) data)))

(defn tag-uploaded [tokeninfo scm-source tag-data]
  {:event_type   {:namespace "cloud.zalando.com"
                  :name      "docker-image-uploaded"
                  :version   "1"}
   :triggered_at (get-date)
   :triggered_by {:type       "USER"
                  :id         (get tokeninfo "uid")
                  :additional {:realm (get tokeninfo "realm")}}
   :payload      {:scm_source (remove-nil scm-source)
                  :tag        (remove-nil tag-data)}})
