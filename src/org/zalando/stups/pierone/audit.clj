(ns org.zalando.stups.pierone.audit
  (:require [clj-time.format :as tf]
            [clj-time.core :as t]))

(defn get-date
  []
  (tf/unparse (tf/formatters :date-time) (t/now)))

(defn tag-uploaded [tokeninfo scm-source tag-data]
  {:event_type   {:namespace "cloud.zalando.com"
                  :name      "docker-image-uploaded"
                  :version   "1"}
   :triggered_at (get-date)
   :triggered_by {:type       "USER"
                  :id         (get tokeninfo "uid")
                  :additional {:realm (get tokeninfo "realm")}}
   :payload      {:scm_source scm-source
                  :tag        tag-data}})
