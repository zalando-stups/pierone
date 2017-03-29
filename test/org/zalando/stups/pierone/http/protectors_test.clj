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
      (fact "when given a config with IIDinfo url, it returns a iid protector fn"
        (protect/iid-protector {:iidinfo-url .url.}) => .impl-fn.
        (provided
          (#'protect/iid-protector-impl .url.) => .impl-fn.))
      (fact "when given a config without IIDinfo url, it returns a pass-through fn"
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
      (fact "verifies iid with IIDinfo if auth header is well-formed and username is 'instance-identity-document'"
        (iid-protector-impl (request-with-auth "instance-identity-document:bar")) => irrelevant
        (provided
          (protect/is-valid-iid? registry-url "bar") => irrelevant :times 1))
      (fact "returns 401 if username is not 'instance-identity-document'"
        (iid-protector-impl (request-with-auth "oauth2:foo")) => unauthorized)
      (fact "returns 401 if IIDinfo returns a status other than 200"
        (iid-protector-impl (request-with-auth "instance-identity-document:bar")) => unauthorized
        (provided
          (http/post registry-url (contains {:form-params {:iid_signature "bar"}})) => {:status 404}))
      (fact "returns request if IIDinfo returns 200"
        (iid-protector-impl .req.) => .req.
        (provided
          .req. =contains=> (request-with-auth "instance-identity-document:bar")
          (http/post registry-url (contains {:form-params {:iid_signature "bar"}})) => {:status 200 :body {}}))
      (fact "makes correct request to IIDinfo"
        (iid-protector-impl (request-with-auth "instance-identity-document:bar")) => irrelevant
        (provided
          (http/post registry-url (contains {:form-params {:iid_signature "bar"}})) => irrelevant))
      (fact "returns 401 if response from IIDinfo is not 200"
        (iid-protector-impl (request-with-auth "instance-identity-document:bar")) => unauthorized
        (provided
          (http/post registry-url (contains {:form-params {:iid_signature "bar"}})) => {:status 404}))
      (fact "returns 401 if request to IIDinfo fails"
        (iid-protector-impl (request-with-auth "instance-identity-document:bar")) => unauthorized
        (provided
          (http/post registry-url irrelevant) =throws=> (new Exception "ARGHH"))))))

