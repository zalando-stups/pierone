(ns org.zalando.stups.pierone.v2-test
  (:require [org.zalando.stups.pierone.test-data :as d]
            [org.zalando.stups.pierone.test-utils :as u]
            [clojure.test :refer :all]
            [clj-http.lite.client :as client]
            [com.stuartsierra.component :as component]))

(deftest v2-test
  (let [system (u/setup d/all-tags
                        d/all-images)]

    ; v2 compatibility check
    (let [result (client/get (u/v2-url "/")
                             (u/http-opts))]
      (= 404 (:status result)))

    ; stop
    (component/stop system)))