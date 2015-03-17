(ns pierone.core-test    (:require
                         [clojure.test :refer :all]
                         [pierone.core :refer :all]
                         [pierone.backend.file :refer :all]
                         [ring.mock.request :as mock]
                         [clojure.data.json :as json]))

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

  (is (= "{\"2.0\":\"123\",\"1.0\":\"123\"}" (:body (with-redefs [list-objects (constantly ["foo/bar/1.0.json", "foo/bar/2.0.json"])
                                                  get-object (constantly (.getBytes "\"123\""))]
                                                 (app (mock/request :get "/v1/repositories/foo/bar/tags")))))))


(deftest test-app-get-image-json

  (is (= 404 (:status (app (mock/request :get "/v1/images/123/json")))))


  (is (= "{}" (:body (with-redefs [get-object (constantly (.getBytes "{}"))]
                                  (app (mock/request :get "/v1/images/123/json")))))))

(deftest test-app-put-image-json

  (is (= 200 (:status (app (-> (mock/request :put "/v1/images/foobar/json")
                               (mock/header "Content-Type" "application/json")
                               (assoc :body (json/write-str {:test "test"}))))))))

(defn- get-mock-image-json-object [path]
  (-> (get {"123.json" "{\"parent\":\"456\"}"
            "456.json" "{}"} path)
      (.getBytes)))

(defn- get-mock-image-layer-object [path]
  (-> (get {"123.layer" "foobar"
            "456.layer" "blubber"} path)
      (.getBytes)))

(deftest test-app-get-ancestry

  (is (= 404 (:status (app (mock/request :get "/v1/images/nonexisting/ancestry")))))

  (is (= "[\"123\",\"456\"]" (:body (with-redefs [get-object get-mock-image-json-object]
                       (app (mock/request :get "/v1/images/123/ancestry")))))))


(deftest test-app-get-layer

  (is (= 404 (:status (app (mock/request :get "/v1/images/nonexisting/layer")))))

  (is (= "foobar" (-> (with-redefs [get-object get-mock-image-layer-object]
                                      (app (mock/request :get "/v1/images/123/layer")))
                      :body
                      org.apache.commons.io.IOUtils/toByteArray
                      String.))))

(deftest test-app-put-layer

  (is (= 200 (:status (app (-> (mock/request :put "/v1/images/foobar/layer")
                               (mock/body "test")))))))

(deftest test-app-put-images

  (is (= 204 (:status (app (-> (mock/request :put "/v1/repositories/foo/bar/images")
                               (assoc :body "[]")))))))

(deftest test-app-put-tag

  (is (= 200 (:status (with-redefs [get-object (constantly nil)]
                        (app (-> (mock/request :put "/v1/repositories/foo/bar/tags/1.0")
                                 (assoc :body "\"123\"")))))))

  (is (= 409 (:status (with-redefs [get-object (constantly (.getBytes "\"123\""))]
                                      (app (-> (mock/request :put "/v1/repositories/foo/bar/tags/1.0")
                                               (assoc :body "\"123\""))))))))

(deftest test-put-checksum
  (is (= 200 (:status (put-image-checksum {})))))