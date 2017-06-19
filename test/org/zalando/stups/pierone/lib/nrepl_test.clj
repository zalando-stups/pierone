(ns org.zalando.stups.pierone.lib.nrepl-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.pierone.lib.nrepl :refer :all]
            [com.stuartsierra.component :as component]))

;; TODO use everywhere
(defmacro with-override-env [override & body]
  `(with-redefs [environ.core/env (merge environ.core/env ~override)]
     ~@body))

(deftest unit-test

  (testing "Starts when port is specified"
    (with-override-env {:nrepl-enabled "true"
                        :nrepl-port    "55055"}
      (let [server (run-nrepl)]
        (is (= 55055 (-> server :server :port)))
        (component/stop server))))

  (testing "Starts on a random port"
    (with-override-env {:nrepl-enabled "true"}
      (let [server (run-nrepl)]
        (is (<= 1024 (-> server :server :port) 65535))
        (component/stop server))))

  (testing "Does not start if enabled=false"
    (with-override-env {:nrepl-enabled "false"
                        :nrepl-port    "55055"}
      (is (nil? (run-nrepl)))))

  )
