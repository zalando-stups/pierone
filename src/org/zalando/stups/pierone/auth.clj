(ns org.zalando.stups.pierone.auth
  (:require [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.auth :as friboo-auth]
            [org.zalando.stups.friboo.user :as user]
            [clojure.core.memoize :as memo]
            [clojure.core.cache :as cache]
            [io.sarnowski.swagger1st.util.api :as api]))

(def cached-require-auth
  ; cache requests to require-auth for 5 minutes
  (memo/fifo friboo-auth/require-auth (cache/ttl-cache-factory {} :ttl 300000) :fifo/threshold 100))

(defn require-scope
  [{:keys [tokeninfo]} scope]
  (let [user   (get tokeninfo "uid")
        scopes (set (get tokeninfo "scope"))]
    (when-not (contains? scopes scope)
      (log/warn "ACCESS DENIED: Missing scope. %s" {:scope scope :user user})
      (api/throw-error 403 "User %s does not have required scope: %s" user scope))))

(defn require-write-access
  "Require write access to team repository"
  [team {:keys [tokeninfo] :as request}]
  ; either user has write access to all (service user)
  ; or user belongs to a team (employee user)
  (when (get-in request [:configuration :tokeninfo-url])
    (when-not (some #{"application.write_all"} (get tokeninfo "scope"))
      (user/require-realms #{"services" "employees"} request)
      (case (get tokeninfo "realm")
        "/services" (do
                      (require-scope request "application.write")
                      (cached-require-auth request team))
        "/employees" (cached-require-auth request team)))))
