(ns org.zalando.stups.pierone.perf-test
  (:require [org.zalando.stups.pierone.test-utils :as u]
            [clojure.test :refer :all]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [clj-http.client :as http]
            [digest :as hash]
            [com.stuartsierra.component :as component]))


(defn now [] (System/currentTimeMillis))

(defn insert-huge-amount-of-data
  [system]
  (let [images (map #(assoc {} :i_id (hash/sha-256 (str "image" %))
                               :i_metadata ""
                               :i_accepted true
                               :i_parent_id (hash/sha-256 (str "image" (inc %))))
                    (range 1000))
        tags (map #(assoc {} :t_team "stups"
                             :t_artifact "kio"
                             :t_name (str "tag" %)
                             :t_image_id (hash/sha-256 (str "image" (+ % 900))))
                  (range 50))]
    (doseq [image images]
      (println (str "Inserting image " (:i_id image)))
      (jdbc/insert! (:db system) :images image))
    (doseq [tag tags]
      (println (str "Inserting tag " (:t_name tag)))
      (jdbc/insert! (:db system) :tags tag))))

(deftest perf-test
  (let [system (u/setup)]
    (u/wipe-db system)
    (insert-huge-amount-of-data system)
    (let [before (now)
          resp (http/get (u/p1-url "/stats/teams"))
          after (now)
          diff (- after before)]
      (println (str diff " ms"))
      (println (str (:body resp)))
      (is (< diff 10000)
          "/stats/teams takes too long")
      (u/wipe-db system)
      (component/stop system))))