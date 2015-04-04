(ns org.zalando.stups.pierone.api
  (:require [org.zalando.stups.friboo.system.http :refer [def-http-component]]
            [org.zalando.stups.pierone.api-v2]
            [org.zalando.stups.pierone.api-v1]))

; define the API component and its dependencies
(def-http-component API "api/pierone-api.yaml" [db storage])

(def default-http-configuration
  {:http-port 8080})
