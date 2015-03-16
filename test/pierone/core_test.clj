(ns pierone.core-test    (:require
                         [clojure.test :refer :all]
                         [pierone.core :refer :all]
                         [ring.mock.request :as mock] ))

(deftest test-index
  (is (= 200 (:status (index {})))))

(deftest test-check-v2
  (is (= 404 (:status (check-v2 {})))))

(deftest test-ping
  (is (= 200 (:status (ping {})))))

(deftest test-put-repo
  (is (= "OK" (:body (put-repo {})))))


