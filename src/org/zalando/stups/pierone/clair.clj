(ns org.zalando.stups.pierone.clair
  (:require [amazonica.aws.sqs :as sqs]
            [digest]
            [io.sarnowski.swagger1st.util.api :as api]
            [cheshire.core :as json]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as a :refer [thread]]
            [org.zalando.stups.pierone.sql :as sql]
            [org.zalando.stups.friboo.log :as log]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.codec.base64 :as b64]
            [slingshot.slingshot :refer [throw+ try+]])
  (:import (java.util.zip GZIPInputStream)
           (java.io EOFException)))

(defn tails [coll]
  (if (empty? coll)
    []
    (lazy-seq (cons coll (tails (rest coll))))))

(defn my-sha256 [data]
  (str "sha256:" (digest/sha-256 data)))

(defn calculate-clair-ids [layers]
  (for [t (tails layers)]
    {:original-id (first t)
     :clair-id    (my-sha256 (apply str t))}))

(defn figure-out-parents [layers]
  (for [[parent current] (partition 2 1 (cons nil layers))]
    {:parent parent :current current}))

;; Returns the list of layer IDs, root at the end
(defn get-layer-hashes-ordered
  [manifest]
  (let [schema-version (:schemaVersion manifest)]
    (condp = schema-version
      1 (map :blobSum (:fsLayers manifest))
      2 (reverse (map :digest (:layers manifest)))
      ; else
      (api/throw-error 400 (str "manifest schema version not compatible with this API: " schema-version)))))

(defn remove-empty-layers [layers]
  (remove #{"sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4"} layers))

(defn prepare-hashes-for-clair [manifest]
  (->> manifest
       get-layer-hashes-ordered                             ; Root in the end
       remove-empty-layers
       calculate-clair-ids                                  ; {:clair-id "foo" :original-id "bar"}
       reverse                                              ; Now root is in the beginning
       figure-out-parents))                                 ; {:parent {...} :current {...}}

(defn create-sqs-message [registry repository artifact {:keys [parent current]}]
  {"Layer" {"Name"       (:clair-id current)
            "Path"       (format "%s/v2/%s/%s/blobs/%s" registry repository artifact (:original-id current))
            "ParentName" (:clair-id parent)
            "Format"     "Docker"}})

(defn send-sqs-message [queue-region queue-url message]
  (sqs/send-message {:endpoint queue-region} :queue-url queue-url
                    :message-body (json/generate-string message {:pretty true})))

(def severity-names
  ["Critical" "High" "Medium" "Low" "Negligible" "Unknown" "Pending"])

(defn severity-rating [severity-name]
  (.indexOf severity-names severity-name))

(defn find-highest-severity [vulnerabilities]
  (->> vulnerabilities
       (map #(get % "Severity"))
       (sort-by severity-rating)
       first))

(defn decode-base64gzip [base64gzipped-str]
  (try
    (slurp (GZIPInputStream. (io/input-stream (b64/decode (.getBytes base64gzipped-str)))))
    (catch EOFException e
      (throw+ {:type ::decode-base64gzip-error} e "Cannot decode base64gzip message."))))

(defn decode-message [message content-type]
  (case content-type
    "application/base64gzip" (decode-base64gzip message)
    "application/json" message
    nil message                                             ; fallback for old version, assume json
    (do
      (log/warn "Cannot decode Clair message, unknown content-type %s" content-type)
      nil)))

;; Returns layer
(defn extract-clair-layer [body]
  (let [{:strs [Message MessageAttributes]} (json/parse-string body)
        content-type (get-in MessageAttributes ["CLAIR.CONTENTTYPE" "Value"])]
    (when-let [decoded-message (decode-message Message content-type)]
      (let [{:strs [Layer]} (json/parse-string decoded-message)]
        Layer))))

;; Returns summary
(defn process-clair-layer [{:strs [Name Features]}]
  (if (empty? Features)
    {:clair-id                  Name
     :severity-fix-available    "clair:CouldntFigureOut"
     :severity-no-fix-available "clair:CouldntFigureOut"}
    (let [features-with-vulnerabilities (seq (filter #(get % "Vulnerabilities") Features))
          vulnerabilities (for [{:strs [Vulnerabilities]} features-with-vulnerabilities
                                v Vulnerabilities]
                            v)
          {noFixAvailable false fixAvailable true} (group-by #(contains? % "FixedBy") vulnerabilities)]
      {:clair-id                  Name
       :severity-fix-available    (or (find-highest-severity fixAvailable) "clair:NoCVEsFound")
       :severity-no-fix-available (or (find-highest-severity noFixAvailable) "clair:NoCVEsFound")})))

(defn store-clair-summary [db {:keys [clair-id severity-fix-available severity-no-fix-available]}]
  (sql/cmd-update-tag-severity! {:clair_id                  clair-id
                             :severity_fix_available    severity-fix-available
                             :severity_no_fix_available severity-no-fix-available} {:connection db}))

(defn receive-sqs-messages [queue-region queue-url]
  (try
    (sqs/receive-message {:endpoint queue-region} :queue-url queue-url :wait-time-seconds 5 :max-number-of-messages 10)
    (catch Exception e
      (log/error e "Error caught during queue polling. %s" {:queue-region queue-region :queue-url queue-url})
      (Thread/sleep 5000)
      {:messages []})))

(defn process-message [db body]
  (try+
    (when-let [layer (extract-clair-layer body)]
      (let [summary (process-clair-layer layer)]
        (store-clair-summary db summary)
        (log/info "Updated layer severity info: %s" summary)
        true))
    (catch [:type ::decode-base64gzip-error] _
      (log/error (:throwable &throw-context)
                "Error caught during queue processing. Deleting the corrupt message from the queue.")
      true)
    (catch Object _
      (log/error (:throwable &throw-context) "Error caught during queue processing. %s" {:message body})
      false)))

(defn receiver-thread-fn [clair-check-result-queue-region clair-check-result-queue-url working-atom processor-fn]
  (loop []
    (when @working-atom
      (let [response (receive-sqs-messages clair-check-result-queue-region clair-check-result-queue-url)]
        (doseq [{:keys [body receipt-handle]} (:messages response)]
          (when (processor-fn body)
            (try
              (sqs/delete-message {:endpoint clair-check-result-queue-region}
                                  :queue-url clair-check-result-queue-url :receipt-handle receipt-handle)
              (catch Exception e
                (log/error e "Error caught when deleting a processed message from the queue")))))
        (recur)))))

(defn start-receiver [{:keys [clair-check-result-queue-region
                                clair-check-result-queue-url
                                clair-receiver-thread-count]
                         :or {clair-receiver-thread-count 20}}
                        db
                        processor-fn]
  (if (some str/blank? [clair-check-result-queue-region clair-check-result-queue-url])
    (log/warn "No API_CLAIR_CHECK_RESULT_QUEUE_REGION or API_CLAIR_CHECK_RESULT_QUEUE_URL, not starting ClairReceiver.")
    (let [working-atom (atom true)]
      (log/info "Starting ClairReceiver with %s threads" clair-receiver-thread-count)
      (dotimes [_ clair-receiver-thread-count]
        (thread (receiver-thread-fn clair-check-result-queue-region clair-check-result-queue-url
                                    working-atom (partial processor-fn db))))
      working-atom)))

(defrecord ClairReceiver [api-config db working-atom]
  component/Lifecycle
  (start [this]
    (assoc this :working-atom (start-receiver api-config db process-message)))
  (stop [this]
    (when working-atom
      (log/info "Stopping ClairReceiver")
      (reset! working-atom false))
    this))

(defn make-clair-receiver []
  (map->ClairReceiver {}))
