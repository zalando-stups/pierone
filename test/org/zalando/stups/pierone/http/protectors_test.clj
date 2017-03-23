(ns org.zalando.stups.pierone.http.protectors-test
  (:require [clojure.test :refer [deftest]]
            [midje.sweet :refer :all]
            [org.zalando.stups.friboo.system.oauth2 :as oauth2]
            [org.zalando.stups.pierone.http.protectors :as protect]
            [clj-http.client :as http])
  (:import (com.netflix.hystrix.exception HystrixBadRequestException)))

(def registry-url "registry")

(def iid-protector-impl
  (#'protect/iid-protector-impl registry-url))

(defn request-with-auth
  [& [val]]
  (if val
    {:headers {"authorization" val}}
    {}))

(def unauthorized
  (contains {:status 401}))

(deftest ^:unit test-http-protectors
  (facts "IIE protector"

    (facts "constructor function"
      (fact "when given a config with cluster registry url, it returns a iid protector fn"
        (protect/iid-protector {:cluster-registry-url .url.}) => .impl-fn.
        (provided
          (#'protect/iid-protector-impl .url.) => .impl-fn.))
      (fact "when given a config without cluster registry url, it returns a pass-through fn"
        (protect/iid-protector {}) => .pass-through-fn.
        (provided
          (oauth2/allow-all) => .pass-through-fn.)))

    (facts "impl function"
      (fact "returns 401 if no auth header is present"
        (iid-protector-impl (request-with-auth nil)) => unauthorized)
      (fact "returns 401 if auth header is malformed"
        (iid-protector-impl (request-with-auth "Bearer token")) => unauthorized
        (iid-protector-impl (request-with-auth "foobarbaz")) => unauthorized
        (iid-protector-impl (request-with-auth {})) => unauthorized
        (iid-protector-impl (request-with-auth 123)) => unauthorized)
      (fact "verifies iid with cluster registry if auth header is well-formed and username is not 'oauth2'"
        (iid-protector-impl (request-with-auth "foo:bar")) => irrelevant
        (provided
          (protect/is-valid-iid? registry-url "foo" "bar") => irrelevant :times 1))
      (fact "returns request if username is 'oauth2'"
        (iid-protector-impl .req.) => .req.
        (provided
          .req. =contains=> {:headers {"authorization" "oauth2:foo"}}))
      (fact "returns 401 if cluster registry returns a status other than 200"
        (iid-protector-impl (request-with-auth "foo:bar")) => unauthorized
        (provided
          (protect/is-valid-iid? registry-url "foo" "bar") => false))
      (fact "returns request if cluster registry returns 200"
        (iid-protector-impl .req.) => .req.
        (provided
          .req. =contains=> {:headers {"authorization" "foo:bar"}}
          (protect/is-valid-iid? registry-url "foo" "bar") => true))
      (fact "returns 401 if request to cluster registry fails"
        (iid-protector-impl (request-with-auth "foo:bar")) => unauthorized
        (provided
          .http-opts. =contains=> {:basic-auth ["foo" "bar"]}
          (http/get registry-url (contains .http-opts.)) =throws=> (new Exception "ARGHH"))))))

