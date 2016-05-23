(ns org.zalando.stups.pierone.clair
  (:require [amazonica.aws.sqs :as sqs]
            [digest]
            [io.sarnowski.swagger1st.util.api :as api]
            [cheshire.core :as json]))

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
            ;; TODO format 'https://$registry/v2/$repository/$artifact/blobs/$layer'
            "Path"       (format "%s/v2/%s/%s/blobs/%s" registry repository artifact (:original-id current))
            "ParentName" (:clair-id parent)
            "Format"     "Docker"}})

(defn send-sqs-message [queue-reqion queue-url message]
  (sqs/send-message {:endpoint queue-reqion} :queue-url queue-url
                    :message-body (json/generate-string message {:pretty true})))
