(ns pierone.backend.file-test
  (:require
    [clojure.test :refer :all]
    [pierone.backend.file :refer :all]
    [pierone.backend :as backend]
    [com.stuartsierra.component :as component])
  (:import
    [java.nio.file Files]
    [java.nio.file.attribute FileAttribute]))

(def foo-bytes (.getBytes "foo"))

(deftest test-file-backend
  (let [temp-dir (Files/createTempDirectory "pierone.backend.file-test" (make-array FileAttribute 0))
        backend (map->FileBackend {:data-path temp-dir})]

    (is (component/start backend))
    (is (component/stop backend))

    (is (= "foo" (do
          (backend/put-object backend "test" foo-bytes)
          (String. (backend/get-object backend "test")))))

    (is (= nil (backend/get-object backend "non-existing-key")))


    (is (= nil (seq (backend/list-objects backend "non-existing-key"))))

    (is (= `("myprefix/blub" "myprefix/second") (do
                   (backend/put-object backend "myprefix/blub" foo-bytes)
                   (backend/put-object backend "anotherprefix/bla" foo-bytes)
                   (backend/put-object backend "myprefix/second" foo-bytes)
                   (sort (seq (backend/list-objects backend "myprefix"))))))))
