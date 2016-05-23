(ns org.zalando.stups.pierone.clair-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.pierone.clair :refer :all])
  (:import (clojure.lang ExceptionInfo)))

(defn fake-sha256 [x]
  (str "[" x "]"))

(def manifest-v1 {:schemaVersion 1 :fsLayers [{:blobSum "a"} {:blobSum "base"}]})
(def manifest-v2 {:schemaVersion 2 :layers [{:digest "base"} {:digest "a"}]})
(def clair-hashes [{:current {:clair-id "[base]", :original-id "base"}
                    :parent  nil}
                   {:current {:clair-id "[abase]", :original-id "a"}
                    :parent  {:clair-id "[base]", :original-id "base"}}])

(deftest wrap-midje-factsI

  (facts "tails"
    (tails nil) => []
    (tails []) => []
    (tails [1]) => [[1]]
    (tails [1 2 3]) => [[1 2 3] [2 3] [3]])

  (facts "calculate-clair-ids"
    (with-redefs [my-sha256 fake-sha256]
      (fact "works"
        (calculate-clair-ids []) => []
        (calculate-clair-ids ["a" "b"])
        => [{:clair-id "[ab]", :original-id "a"} {:clair-id "[b]", :original-id "b"}])))

  (facts "figure-out-parents"
    (fact "works"
      (figure-out-parents []) => []
      (figure-out-parents [..a..])
      => [{:current ..a.., :parent nil}]
      (figure-out-parents [..a.. ..b..])
      => [{:current ..a.., :parent nil} {:current ..b.., :parent ..a..}]))

  (facts "get-layer-hashes-ordered"
    (fact "works for version 1"
      (get-layer-hashes-ordered manifest-v1)
      => ["a" "base"])
    (fact "works for version 2"
      (get-layer-hashes-ordered manifest-v2)
      => ["a" "base"])
    (fact "throws an exception in other cases"
      (get-layer-hashes-ordered {:schemaVersion 3})
      => (throws ExceptionInfo)))

  (facts "prepare-hashes-for-clair"
    (with-redefs [my-sha256 fake-sha256]
      (fact "works for version 1"
        (prepare-hashes-for-clair manifest-v1)
        => clair-hashes)
      (fact "works for version 2"
        (prepare-hashes-for-clair manifest-v2)
        => clair-hashes)))

  (facts "create-sqs-message"
    (create-sqs-message "https://pierone.example.com" "repo" "artifact" (second clair-hashes))
    => {"Layer" {"Name"       "[abase]",
                 "Path"       "https://pierone.example.com/v2/repo/artifact/blobs/a",
                 "ParentName" "[base]",
                 "Format"     "Docker"}})

  )

