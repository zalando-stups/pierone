(ns pierone.backend.s3-test
  (:require
    [clojure.test :refer :all]
    [pierone.backend.s3 :refer :all]
    [pierone.backend :as backend]
    [amazonica.aws.s3 :as aws]
    [com.stuartsierra.component :as component])
  (:import
    [java.nio.file Files]
    [java.nio.file.attribute FileAttribute]
    (java.io ByteArrayInputStream)
    (com.amazonaws.services.s3.model AmazonS3Exception)))


(defn throw-key-not-found-exception [& args]
  (throw (doto (AmazonS3Exception. "The specified key does not exist")
           (.setStatusCode 404))))

(deftest test-s3-backend
  (let [
        backend (new-s3-backend "my-bucket" "my-region")]

    (is (component/start backend))
    (is (component/stop backend))

    (with-redefs [aws/get-object throw-key-not-found-exception]
      (is (= nil (backend/get-object backend "test"))))

    (with-redefs [aws/get-object (constantly {:input-stream (ByteArrayInputStream. (.getBytes "foo"))})
                  aws/put-object (constantly nil)
                  aws/list-objects (constantly {:object-summaries []})
                  ]

      (is (= "foo" (String. (backend/get-object backend "test"))))

      (is (= nil (backend/put-object backend "non-existing-key" (.getBytes "foo"))))

      (is (= nil (seq (backend/list-objects backend "non-existing-key")))))))

