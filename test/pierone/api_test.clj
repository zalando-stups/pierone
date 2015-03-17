(ns pierone.api-test
  (:require
    [clojure.test :refer :all]
    [pierone.api :refer :all]
    [pierone.backend :as backend]
    [pierone.backend.file :refer :all]
    [ring.mock.request :as mock]
    [clojure.data.json :as json]))

(defn mock-get-object [key] nil)
(defn mock-put-object [key bytes] nil)
(defn mock-list-objects [prefix] nil)

(defrecord MockBackend [functions]
  backend/Backend
  (get-object [backend key]
    (mock-get-object key))
  (put-object [backend key bytes]
    (mock-put-object key bytes))
  (list-objects [backend prefix]
    (mock-list-objects prefix)))

(defn new-mock-app []
  (new-app "api.yaml" (map->MockBackend {})))

(def mock-app (new-mock-app))

(deftest test-index
  (is (= 200 (:status (index {} {})))))

(deftest test-check-v2
  (is (= 404 (:status (check-v2 {} {})))))

(deftest test-ping
  (is (= 200 (:status (ping {} {})))))

(deftest test-put-repo
  (is (= "OK" (:body (put-repo {} {})))))

(deftest test-read-tag
  (is (= {"1.0" "1234"} (with-redefs [backend/get-object (constantly (.getBytes "\"1234\""))]
                          (read-tag {} "foo/bar/1.0.json")))))

(deftest test-app-get-tags

  (is (= 404 (:status (mock-app (mock/request :get "/v1/repositories/NON/EXISTING/tags")))))

  (is (= "{\"2.0\":\"123\",\"1.0\":\"123\"}" (:body (with-redefs [mock-list-objects (constantly ["foo/bar/1.0.json", "foo/bar/2.0.json"])
                                                                  mock-get-object (constantly (.getBytes "\"123\""))]
                                                      (mock-app (mock/request :get "/v1/repositories/foo/bar/tags")))))))

(deftest test-get-image-json-data

  (is (= {} (with-redefs [backend/get-object (constantly (.getBytes "{}"))]
              (get-image-json-data {} "123")))))

(deftest test-app-get-image-json

  (is (= 404 (:status (mock-app (mock/request :get "/v1/images/123/json")))))

  (is (= "{\"foo\":\"bar\"}" (:body (with-redefs [mock-get-object (constantly (.getBytes "{\"foo\":\"bar\"}"))]
                       (mock-app (mock/request :get "/v1/images/123/json")))))))

(deftest test-app-put-image-json

  (is (= 200 (:status (mock-app (-> (mock/request :put "/v1/images/foobar/json")
                                      (mock/header "Content-Type" "application/json")
                                      (assoc :body (json/write-str {:test "test"}))))))))

(defn- get-mock-image-json-object [path]
  (-> (get {"images/123.json" "{\"parent\":\"456\"}"
            "images/456.json" "{}"} path)
      (.getBytes)))

(defn- get-mock-image-layer-object [path]
  (-> (get {"images/123.layer" "foobar"
            "images/456.layer" "blubber"} path)
      (.getBytes)))

(deftest test-app-get-ancestry

  (is (= 404 (:status (mock-app (mock/request :get "/v1/images/nonexisting/ancestry")))))

  (is (= "[\"123\",\"456\"]" (:body (with-redefs [mock-get-object get-mock-image-json-object]
                                      (mock-app (mock/request :get "/v1/images/123/ancestry")))))))


(deftest test-app-get-layer

  (is (= 404 (:status (mock-app (mock/request :get "/v1/images/nonexisting/layer")))))

  (is (= "foobar" (-> (with-redefs [mock-get-object get-mock-image-layer-object]
                        (mock-app (mock/request :get "/v1/images/123/layer")))
                      :body
                      org.apache.commons.io.IOUtils/toByteArray
                      String.))))

(deftest test-app-put-layer

  (is (= 200 (:status (mock-app (-> (mock/request :put "/v1/images/foobar/layer")
                                      (mock/body "test")))))))

(deftest test-app-put-images

  (is (= 204 (:status (mock-app (-> (mock/request :put "/v1/repositories/foo/bar/images")
                                      (assoc :body "[]")))))))

(deftest test-app-get-images

  (is (= 200 (:status (mock-app (mock/request :get "/v1/repositories/foo/bar/images"))))))

(deftest test-app-put-tag

  (is (= 200 (:status (with-redefs [mock-get-object (constantly nil)]
                        (mock-app (-> (mock/request :put "/v1/repositories/foo/bar/tags/1.0")
                                        (assoc :body "\"123\"")))))))

  (is (= 409 (:status (with-redefs [mock-get-object (constantly (.getBytes "\"123\""))]
                        (mock-app (-> (mock/request :put "/v1/repositories/foo/bar/tags/1.0")
                                        (assoc :body "\"456\""))))))))

(deftest test-put-checksum
  (is (= 200 (:status (put-image-checksum {} {})))))

(deftest test-app-search

  (is (= 200 (:status (with-redefs [mock-list-objects (constantly nil)]
                        (mock-app (mock/request :get "/v1/search?q=")))))))
