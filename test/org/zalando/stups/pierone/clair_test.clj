(ns org.zalando.stups.pierone.clair-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.pierone.clair :refer :all]))

(deftest wrap-midje-facts

  (facts "tails"
         (tails [1 2 3]) => [[1 2 3] [2 3] [3]])

  )

