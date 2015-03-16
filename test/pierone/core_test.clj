(ns pierone.core-test    (:require
                         [clojure.test :refer :all]
                         [pierone.core :refer :all]))

(deftest test-check-v2
  (is 404 (:status check-v2)))


