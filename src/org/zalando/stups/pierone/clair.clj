(ns org.zalando.stups.pierone.clair
  (:require [amazonica.aws.sqs :as sqs]
            [digest]
            [io.sarnowski.swagger1st.util.api :as api]
            [cheshire.core :as json]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as a :refer [chan go alts!! <!! >!! thread close! go-loop]]
            [org.zalando.stups.pierone.sql :as sql]
            [org.zalando.stups.friboo.log :as log]
            [clojure.string :as str]))

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

(defn send-sqs-message [queue-reqion queue-url message]
  (sqs/send-message {:endpoint queue-reqion} :queue-url queue-url
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

;; Returns summary
(defn process-clair-layer [{:strs [Name Features]}]
  (if (empty? Features)
    {:clair-id                  Name
     :severity-fix-available    "clair:CouldntFigureOut"
     :severity-no-fix-available "clair:CouldntFigureOut"}
    (if-let [features-with-vulnerabilities (seq (filter #(get % "Vulnerabilities") Features))]
      (let [vulnerabilities (for [{:strs [Vulnerabilities]} features-with-vulnerabilities
                                  v Vulnerabilities]
                              v)
            {noFixAvailable false fixAvailable true} (group-by #(contains? % "FixedBy") vulnerabilities)]
        {:clair-id                  Name
         :severity-fix-available    (find-highest-severity fixAvailable)
         :severity-no-fix-available (find-highest-severity noFixAvailable)})
      {:clair-id                  Name
       :severity-fix-available    "clair:NoCVEsFound"
       :severity-no-fix-available "clair:NoCVEsFound"})))

;; Returns layer
(defn extract-clair-layer [{:keys [body receipt-handle]}]
  (let [{:strs [Message]} (json/parse-string body)
        {:strs [Layer]} (json/parse-string Message)]
    [Layer receipt-handle]))

(defn store-clair-summary [db {:keys [clair-id severity-fix-available severity-no-fix-available]}]
  (sql/update-tag-severity! {:clair_id                  clair-id
                             :severity_fix_available    severity-fix-available
                             :severity_no_fix_available severity-no-fix-available} {:connection db}))

(defn processor-thread-fn [{:keys [clair-check-result-queue-url clair-check-result-queue-reqion]} db receive-ch]
  (loop []
    (when-let [message (<!! receive-ch)]
      (try
        (let [[layer receipt-handle] (extract-clair-layer message)
              summary (process-clair-layer layer)]
          (store-clair-summary db summary)
          (sqs/delete-message {:endpoint clair-check-result-queue-reqion}
                              :queue-url clair-check-result-queue-url :receipt-handle receipt-handle)
          (log/info "Updated layer severity info: %s" summary))
        (catch Exception e
          (log/error e "Error caught during queue processing. %s" {:message message})))
      (recur)))
  (log/debug "Stopping processor thread."))

(defn read-sqs-message [queue-reqion queue-url]
  (try
    (sqs/receive-message {:endpoint queue-reqion} :queue-url queue-url :wait-time-seconds 20)
    (catch Exception e
      (log/error e "Error caught during queue polling. %s" {:queue-region queue-reqion :queue-url queue-url})
      (Thread/sleep 5000)
      {:messages []})))

;; Creates a channel and returns it
;; In the background gets messages from the queue in batches and puts them one by one into the channel
(defn sqs-receive-chan [stop-ch queue-reqion queue-url]
  (let [out-ch (chan)]
    (thread
      (loop []
        (let [[messages _] (alts!! [stop-ch (go (read-sqs-message queue-reqion queue-url))])]
          (if-not messages
            (close! out-ch)
            (do
              (doseq [m (:messages messages)]
                (>!! out-ch m))
              (recur)))))
      (log/debug "Stopping receiver thread."))
    out-ch))

(defn start-receiver [{:keys [clair-check-result-queue-reqion clair-check-result-queue-url] :as api-config} db]
  (when (and (not (str/blank? clair-check-result-queue-reqion)) (not (str/blank? clair-check-result-queue-url)))
    (let [stop-ch (chan)
          receive-ch (sqs-receive-chan stop-ch clair-check-result-queue-reqion clair-check-result-queue-url)]
      (thread (processor-thread-fn api-config db receive-ch))
      stop-ch)))

(defrecord ClairReceiver [api-config db stop-ch]
  component/Lifecycle
  (start [this]
    (log/debug "Starting Receiver")
    (assoc this :stop-ch (start-receiver api-config db)))
  (stop [this]
    (log/debug "Stopping Receiver")
    (when stop-ch
      (close! stop-ch))
    this))

(defn make-clair-receiver []
  (map->ClairReceiver {}))
