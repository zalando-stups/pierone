(ns org.zalando.stups.pierone.sns
  (:require [amazonica.aws.sns :as sns]
            [cheshire.core :as json]
            [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.log :as log]
            [clojure.string :as str]
            [clojure.data.codec.base64 :as b64]))

(defn publish-cdp-sns 
  [topic message]
  (sns/publish :topic-arn (str "arn:aws:sns:eu-west-1:FROMCONFIG:" topic)
           :subject "image-pushed"
           :message message
           :message-attributes {})
  )
