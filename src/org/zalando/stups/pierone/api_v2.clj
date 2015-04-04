(ns org.zalando.stups.pierone.api-v2
  (:require [org.zalando.stups.friboo.system.http :refer [def-http-component]]
            [ring.util.response :refer :all]
            [org.zalando.stups.friboo.ring :refer :all]))

;; Docker Registry API v2

(defn ping
  "Checks for compatibility with version 2."
  [_ _ _ _]
  (-> (response "Registry API v2 is not implemented")
      (content-type-json)
      (status 404)))
