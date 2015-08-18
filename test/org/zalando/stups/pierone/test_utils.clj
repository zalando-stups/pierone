(ns org.zalando.stups.pierone.test-utils
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [clj-http.client :as client]
            [org.zalando.stups.pierone.test-data :as d]
            [org.zalando.stups.pierone.core :refer [run]]))

(def base-url "http://localhost:8080")

(defn p1-url [& path]
  (let [url (apply str base-url path)]
    (println (str "[request] " url))
    url))

(defn v1-url [& path]
  (let [url (apply str base-url "/v1" path)]
    (println (str "[request] " url))
    url))

(defn v2-url [& path]
  (let [url (apply str base-url "/v2" path)]
    (println (str "[request] " url))
    url))

(defn http-opts [& args]
  (let [default {:throw-exceptions false
                 :debug false}
        body    (first args)
        content (second args)]
    (if (nil? body)
        default
        (assoc default :body body
                       :content-type content))))
          

(defn wrap-quotes [string]
  (str "\"" string "\""))

; some expectation helpers

(defn expect-headers [response]
  (is (= "0.6.3"
         (get (:headers response)
              "x-docker-registry-version"))
      (apply str "response missing registry version header: " response))
  (is (= "localhost:8080"
         (get (:headers response)
              "x-docker-endpoints"))
      (apply str "response missing endpoints header: " response)))

(defn expect [status-code response]
  (is (= (:status response)
         status-code)
      (apply str "response of wrong status: " response))
  (expect-headers response)
  (:body response))

(defn wipe-db
  [system]
  (println "Deleting all tags and images")
  (jdbc/delete! (:db system) :tags ["t_name IS NOT NULL"])
  (jdbc/delete! (:db system) :images ["i_id IS NOT NULL"])
  system)

(defn delete-test-data
  [system]
  (doseq [tag d/all-tags]
    (jdbc/delete! (:db system) :tags ["t_team = ? AND t_artifact = ? AND t_name = ?" (:team tag) (:artifact tag) (:name tag)])
    (println "Deleted tag" (:team tag) "/" (:artifact tag) ":" (:name tag) "from old tests if existed."))
  (doseq [image d/all-images]
    (jdbc/delete! (:db system) :images ["i_id = ?" (:id image)])
    (println "Deleted image" (:id image) "from old tests if existed."))
  system)

; setup
(defn setup
  "Starts Pierone."
  []
  (run {}))

(defn push-images
  "Pushes images and verifies"
  [images]
  (doseq [image images]
    (expect 404
            (client/get (v1-url "/images/"
                                (:id image)
                                "/json")
                        (http-opts)))
    (expect 200
            (client/put (v1-url "/images/"
                                (:id image)
                                "/json")
                        (http-opts (:metadata image)
                                    :json)))
    (expect 200
            (client/put (v1-url "/images/"
                                (:id image)
                                "/layer")
                        (http-opts (io/input-stream (:data image)))))))
