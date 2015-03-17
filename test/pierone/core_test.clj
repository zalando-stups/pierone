(ns pierone.core-test
  (:require
    [clojure.test :refer :all]
    [pierone.core :refer :all]
    [pierone.backend.file :refer :all]
    [ring.mock.request :as mock]
    [clojure.data.json :as json])
  (:import (pierone.backend.file FileBackend)))

(deftest test-new-system
  (is (->> (new-system "api.yaml" new-file-backend)
           :backend
           (instance? FileBackend))))
