(ns org.zalando.stups.pierone.http.protectors
  (:require [org.zalando.stups.friboo.system.oauth2 :as oauth2]
            [clj-http.client :as http]
            [com.netflix.hystrix.core :as hystrix]
            [org.zalando.stups.friboo.log :as log]
            [io.sarnowski.swagger1st.util.api :as api]
            [clojure.string :as str])
  (:import [com.netflix.hystrix.exception HystrixRuntimeException]))

(defn is-valid-iid-impl
  "Makes HTTP request to Cluster Registry and returns true if response status is 200.
  Timeouts are handled by surrounding Hystrix command."
  [url signature]
  (->
    (http/post url
      {:content-type     :json
       :form-params      {:iid_signature signature}
       :throw-exceptions false})
    :status
    (= 200)))

(hystrix/defcommand is-valid-iid?
  [url signature]
  :hystrix/fallback-fn (constantly false)
  :hystrix/group-key :cluster-registry
  :hystrix/command-key :verify-iid
  (is-valid-iid-impl url signature))

(defn- auth-header [req]
  (get-in req [:headers "authorization"]))

(defn- has-auth-header? [req]
  (some? (auth-header req)))

(defn- parse-auth-header [header]
  (str/split header #":"))

(defn- has-well-formed-auth-header? [req]
  "Auth header is expected to be of form 'user:passwd' after processing
  by this time (done by http/map-authorization-header)."
  (let [header (auth-header req)]
    (and
      (string? header)
      (->
        (parse-auth-header header)
        count
        (= 2)))))

(defn- iid-protector-impl
  "IID protector.
  IID & its signature are received via Basic Auth header.
  Sends IID+Sig to Cluster Registry to verify them.
  Grants access based on Cluster Reg's decision."
  [cluster-reg-url]
  (fn [req & _]
    (cond
      (not (has-auth-header? req))
      (api/error 401 "")

      (not (has-well-formed-auth-header? req))
      (api/error 401 "")

      (not= "instance-identity-document" (-> req auth-header parse-auth-header first))
      (api/error 401 "")

      :else
      (try
        (let [[username password] (-> req auth-header parse-auth-header)]
          (log/info "Checking IID %s %s" username password)
          (if (is-valid-iid? cluster-reg-url password)
            req
            (do
              (log/warn "Invalid IID %s %s" username password)
              (api/error 401 "Computer says no"))))
        ; This is thrown by Hystrix e.g. when it can't enqueue a command.
        ; We don't know about other excpetions that could occur and don't handle them.
        ; One could choose to not handle this Exception and leave it to Swagger1st
        ; middleware, but this would end up in a 503 status.
        (catch HystrixRuntimeException _
          (api/error 401 ""))))))

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
