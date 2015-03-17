(ns pierone.core-test
  (:require
    [clojure.test :refer :all]
    [pierone.core :refer :all]
    [pierone.backend.file :refer :all]
    [ring.mock.request :as mock]
    [clojure.data.json :as json])
  (:import (pierone.backend.file FileBackend)
           (pierone.backend.s3 S3Backend)))

(deftest test-new-system
  (is (->> (new-system {})
           :backend
           (instance? FileBackend)))

  (is (->> (new-system {:backend "s3"})
           :backend
           (instance? S3Backend))))
