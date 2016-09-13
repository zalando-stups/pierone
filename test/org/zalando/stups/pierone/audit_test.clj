(ns org.zalando.stups.pierone.audit-test
  (:require [clojure.test :refer [deftest]]
            [midje.sweet :refer :all]
            [org.zalando.stups.pierone.audit :as audit]))

(deftest test-audit
  (facts "audit"
    (fact "tag-uploaded creates correct structure"
      (audit/tag-uploaded .tokeninfo. .scm-source. .tag-data.) => (contains {:event_type {:namespace "cloud.zalando.com"
                                                                                          :name "docker-image-uploaded"
                                                                                          :version "1"}
                                                                             :triggered_at #"\d{4}-d\{2}-\d{2}T\d{2}-\d{2}-\d{2}Z"
                                                                             :triggered_by {:type "USER"
                                                                                            :id .uid.
                                                                                            :additional {:realm .realm.}}
                                                                             :payload {:scm_source .scm-source.
                                                                                       :tag .tag-data.}})
      (provided
        .tokeninfo. =contains=> {"uid" .uid.
                                 "realm" .realm.}))))
