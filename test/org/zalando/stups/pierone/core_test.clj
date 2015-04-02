(ns org.zalando.stups.pierone.core-test
  (:require
    [clojure.test :refer :all]
    [org.zalando.stups.pierone.core :refer :all]
    [org.zalando.stups.pierone.backend.file :refer :all]
    [ring.mock.request :as mock]
    [clojure.data.json :as json])
  (:import (org.zalando.stups.pierone.backend.file FileBackend)
           (org.zalando.stups.pierone.backend.s3 S3Backend)))

(deftest test-new-system
  (is (->> (new-system {})
           :backend
           (instance? FileBackend)))

  (is (->> (new-system {:backend {:s3-bucket-name "testbucket"}})
           :backend
           (instance? S3Backend))))
