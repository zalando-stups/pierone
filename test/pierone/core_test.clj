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

(deftest test-read-tag
  (is (= {"1.0" "1234"} (with-redefs [get-object (constantly (.getBytes "\"1234\""))]
                         (read-tag "foo/bar/1.0.json"))))
  )

(deftest test-app-get-tags

  (is (= 404 (:status (app (mock/request :get "/v1/repositories/NON/EXISTING/tags")))))

  (is (= "{\"1.0\":\"123\"}" (:body (with-redefs [list-objects (constantly ["foo/bar/1.0.json"])
                                            get-object (constantly (.getBytes "\"123\""))]
                                (app (mock/request :get "/v1/repositories/foo/bar/tags")))))))


(deftest test-app-get-image-json

  (is (= 404 (:status (app (mock/request :get "/v1/images/123/json")))))


  (is (= "{}" (:body (with-redefs [get-object (constantly (.getBytes "{}"))]
                                      (app (mock/request :get "/v1/images/123/json")))))))

(defn- get-mock-image-json-object [path]
  (-> (get {"123.json" "{\"parent\":\"456\"}"
            "456.json" "{}"} path)
      (.getBytes)))


(deftest test-app-get-ancestry

  (is (= 404 (:status (app (mock/request :get "/v1/images/nonexisting/ancestry")))))

  (is (= "[\"123\",\"456\"]" (:body (with-redefs [get-object get-mock-image-json-object]
                       (app (mock/request :get "/v1/images/123/ancestry")))))))
