(ns org.zalando.stups.pierone.util-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.friboo.auth :as fauth]
            [org.zalando.stups.pierone.auth :as auth]
            [clojure.core.cache :as cache]
            [org.zalando.stups.friboo.auth :as friboo-auth]
            [clojure.core.memoize :as memo]))

(def request {:configuration {:tokeninfo-url "token.info"}
              :tokeninfo     {"realm" "/employees"
                              "scope" ["foo.bar"]}})

(deftest wrap-midje-facts

  (facts "auth/require-scope"
    (fact "throws if scope is not in tokeninfo"
      (auth/require-scope request "foo") => (throws Exception))
    (fact "does not throw if scope is in tokeninfo"
      (auth/require-scope request "foo.bar") => nil))

  (facts "auth/require-write-access"
    (fact "is cached"
      (auth/require-write-access "team" request) => nil
      (auth/require-write-access "team" request) => nil
      (provided
        (fauth/require-auth request "team") => nil :times 1))
    (fact "only works with tokeninfo url configured"
      (auth/require-write-access "foo" {}) => nil)
    (fact "calls require-auth"
      (auth/require-write-access "team" request) => (throws Exception)
      (provided
        (auth/cached-require-auth request "team") =throws=> (Exception.))))

  )
