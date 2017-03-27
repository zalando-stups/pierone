(ns org.zalando.stups.pierone.http.protectors-test
  (:require [clojure.test :refer [deftest]]
            [midje.sweet :refer :all]
            [org.zalando.stups.friboo.system.oauth2 :as oauth2]
            [org.zalando.stups.pierone.http.protectors :as protect]
            [clj-http.client :as http]))

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
  (facts "IID protector"

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
      (fact "verifies iid with cluster registry if auth header is well-formed and username is 'instance-identity-document'"
        (iid-protector-impl (request-with-auth "instance-identity-document:bar")) => irrelevant
        (provided
          (protect/is-valid-iid? registry-url "bar") => irrelevant :times 1))
      (fact "returns 401 if username is not 'instance-identity-document'"
        (iid-protector-impl (request-with-auth "oauth2:foo")) => unauthorized)
      (fact "returns 401 if cluster registry returns a status other than 200"
        (iid-protector-impl (request-with-auth "instance-identity-document:bar")) => unauthorized
        (provided
          (http/post registry-url (contains {:form-params {:iid_signature "bar"}})) => {:status 404}))
      (fact "returns 401 if cluster registry returns 200 and contains {'verified': true}"
        (iid-protector-impl (request-with-auth "instance-identity-document:bar")) => unauthorized
        (provided
          (http/post registry-url (contains {:form-params {:iid_signature "bar"}})) => {:status 200 :body {:verified false}}))
      (fact "returns request if cluster registry returns 200 and contains {'verified': true}"
        (iid-protector-impl .req.) => .req.
        (provided
          .req. =contains=> (request-with-auth "instance-identity-document:bar")
          (http/post registry-url (contains {:form-params {:iid_signature "bar"}})) => {:status 200 :body {:verified true}}))
      (fact "makes correct request to cluster registry"
        (iid-protector-impl (request-with-auth "instance-identity-document:bar")) => irrelevant
        (provided
          (http/post registry-url (contains {:form-params {:iid_signature "bar"}})) => irrelevant))
      (fact "returns 401 if response from cluster registry is not 200"
        (iid-protector-impl (request-with-auth "instance-identity-document:bar")) => unauthorized
        (provided
          (http/post registry-url (contains {:form-params {:iid_signature "bar"}})) => {:status 404}))
      (fact "returns 401 if request to cluster registry fails"
        (iid-protector-impl (request-with-auth "instance-identity-document:bar")) => unauthorized
        (provided
          (http/post registry-url irrelevant) =throws=> (new Exception "ARGHH"))))))

