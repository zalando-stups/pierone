(ns org.zalando.stups.pierone.http.protectors
  (:require [org.zalando.stups.friboo.system.oauth2 :as oauth2]
            [clj-http.client :as http]
            [org.zalando.stups.friboo.log :as log]
            [io.sarnowski.swagger1st.util.api :as api]
            [clojure.string :as str]))

(defn is-valid-iid?
  [_ iid iid-sig]
  (and (= iid "nikolaus") (= iid-sig "password")))


(defn- iid-protector-impl
  [cluster-reg-url]
  (fn [req _ _]
    (if-let [auth-header (get-in req [:headers "authorization"])]
      (if (string? auth-header)
        (let [[username password] (str/split auth-header #":")]
          (log/info "Checking IID %s %s" username password)
          (if (not= username "oauth2")
            ; send to cluster reg
            (if (is-valid-iid? cluster-reg-url username password)
              req
              (do
                (log/warn "IID is invalid")
                (api/error 401 "Computer says no")))
            req)))
      (do
        (log/warn "No auth header")
        (api/error 401 "Computer says no")))))

(defn iid-protector [configuration]
  (if-let [cluster-reg-url (:cluster-registry-url configuration)]
    (do
      (log/info "Checking IIDs against %s." cluster-reg-url)
      (iid-protector-impl cluster-reg-url))
    (do
      (log/warn "No Cluster Registry URL set, cannot authenticate with IID!")
      (oauth2/allow-all))))


(defn oauth2-protector [configuration]
  (if (:tokeninfo-url configuration)
    (do
      (log/info "Checking access tokens against %s." (:tokeninfo-url configuration))
      (oauth2/oauth-2.0 configuration oauth2/check-corresponding-attributes
        :resolver-fn oauth2/resolve-access-token))
    (do
      (log/warn "No token info URL configured; NOT ENFORCING SECURITY!")
      (oauth2/allow-all))))
