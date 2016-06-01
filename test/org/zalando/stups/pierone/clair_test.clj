(ns org.zalando.stups.pierone.clair-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.pierone.clair :refer :all]
            [amazonica.aws.sqs :as sqs]
            [clojure.core.async :as a])
  (:import (clojure.lang ExceptionInfo)
           (java.io EOFException)))

(defn fake-sha256 [x]
  (str "[" x "]"))

(def manifest-v1 {:schemaVersion 1 :fsLayers [{:blobSum "a"} {:blobSum "base"}]})
(def manifest-v2 {:schemaVersion 2 :layers [{:digest "base"} {:digest "a"}]})
(def clair-hashes [{:current {:clair-id "[base]", :original-id "base"}
                    :parent  nil}
                   {:current {:clair-id "[abase]", :original-id "a"}
                    :parent  {:clair-id "[base]", :original-id "base"}}])

(deftest wrap-midje-facts

  (facts "tails"
    (tails nil) => []
    (tails []) => []
    (tails [1]) => [[1]]
    (tails [1 2 3]) => [[1 2 3] [2 3] [3]])

  (facts "calculate-clair-ids"
    (with-redefs [my-sha256 fake-sha256]
      (fact "works"
        (calculate-clair-ids []) => []
        (calculate-clair-ids ["a" "b"])
        => [{:clair-id "[ab]", :original-id "a"} {:clair-id "[b]", :original-id "b"}])))

  (facts "figure-out-parents"
    (fact "works"
      (figure-out-parents []) => []
      (figure-out-parents [..a..])
      => [{:current ..a.., :parent nil}]
      (figure-out-parents [..a.. ..b..])
      => [{:current ..a.., :parent nil} {:current ..b.., :parent ..a..}]))

  (facts "get-layer-hashes-ordered"
    (fact "works for version 1"
      (get-layer-hashes-ordered manifest-v1)
      => ["a" "base"])
    (fact "works for version 2"
      (get-layer-hashes-ordered manifest-v2)
      => ["a" "base"])
    (fact "throws an exception in other cases"
      (get-layer-hashes-ordered {:schemaVersion 3})
      => (throws ExceptionInfo)))

  (facts "prepare-hashes-for-clair"
    (with-redefs [my-sha256 fake-sha256]
      (fact "works for version 1"
        (prepare-hashes-for-clair manifest-v1)
        => clair-hashes)
      (fact "works for version 2"
        (prepare-hashes-for-clair manifest-v2)
        => clair-hashes)))

  (facts "create-sqs-message"
    (create-sqs-message "https://pierone.example.com" "repo" "artifact" (second clair-hashes))
    => {"Layer" {"Name"       "[abase]",
                 "Path"       "https://pierone.example.com/v2/repo/artifact/blobs/a",
                 "ParentName" "[base]",
                 "Format"     "Docker"}})

  (facts ""
    (find-highest-severity [{"Severity" "Foo"} {"Severity" "Critical"}]) => "Foo"
    (find-highest-severity [{"Severity" "Low"} {"Severity" "High"}]) => "High")

  (facts "decode-base64gzip"
    (decode-base64gzip "H4sIANmfTVcAA/NIzcnJBwCCidH3BQAAAA==") => "Hello"
    (decode-base64gzip "H4sIAAufTVcAA/NIzcnJ5wIAFjWWMQYA") => (throws ExceptionInfo "Cannot decode base64gzip message."))

  (facts "decode-message"
    (decode-message "H4sIANmfTVcAA/NIzcnJBwCCidH3BQAAAA==" "application/base64gzip") => "Hello"
    (decode-message "Hello" "application/json") => "Hello"
    (decode-message "Hello" nil) => "Hello"
    (decode-message "Hello" "unknown/content") => nil)

  (facts "extract-clair-layer"
    (extract-clair-layer "{
           \"Message\" : \"H4sIAB+hTVcAA6tW8kmsTC1SslJKy89XqgUA6BrbmQ8AAAA=\",
           \"MessageAttributes\" : {
             \"CLAIR.CONTENTTYPE\" : {\"Value\":\"application/base64gzip\"}}}")
    => "foo"
    (extract-clair-layer "{
           \"Message\" : \"{\\\"Layer\\\":\\\"foo\\\"}\",
           \"MessageAttributes\" : {
             \"CLAIR.CONTENTTYPE\" : {\"Value\":\"application/json\"}}}")
    => "foo"
    (extract-clair-layer "{
           \"Message\" : \"{\\\"Layer\\\":\\\"foo\\\"}\"}")
    => "foo"
    (extract-clair-layer "{
           \"Message\" : \"foo\",
           \"MessageAttributes\" : {
             \"CLAIR.CONTENTTYPE\" : {\"Value\":\"unknown/content\"}}}")
    => nil
    (extract-clair-layer "{
           \"Message\" : \"H4sIAAufTVcAA/NIzcnJ5wIAFjWWMQYA\",
           \"MessageAttributes\" : {
             \"CLAIR.CONTENTTYPE\" : {\"Value\":\"application/base64gzip\"}}}")
    => (throws ExceptionInfo "Cannot decode base64gzip message."))

  (facts "process-clair-layer"
    (process-clair-layer {"Name" "foo"})
    => {:clair-id "foo" :severity-fix-available "clair:CouldntFigureOut" :severity-no-fix-available "clair:CouldntFigureOut"}
    (process-clair-layer {"Name" "foo" "Features" []})
    => {:clair-id "foo" :severity-fix-available "clair:CouldntFigureOut" :severity-no-fix-available "clair:CouldntFigureOut"}
    (process-clair-layer {"Name" "foo" "Features" [{}]})
    => {:clair-id "foo" :severity-fix-available "clair:NoCVEsFound" :severity-no-fix-available "clair:NoCVEsFound"}
    (process-clair-layer {"Name" "foo" "Features" [{"Vulnerabilities" [{"Severity" "Low"}]}]})
    => {:clair-id "foo" :severity-fix-available "clair:NoCVEsFound" :severity-no-fix-available "Low"}
    (process-clair-layer {"Name" "foo" "Features" [{"Vulnerabilities" [{"Severity" "Low" "FixedBy" "foo"}]}]})
    => {:clair-id "foo", :severity-fix-available "Low", :severity-no-fix-available "clair:NoCVEsFound"}
    (process-clair-layer {"Name" "foo" "Features" [{"Vulnerabilities" [{"Severity" "Low" "FixedBy" "foo"}
                                                                       {"Severity" "Low"}]}]})
    => {:clair-id "foo" :severity-fix-available "Low" :severity-no-fix-available "Low"})

  (facts "processor-thread-fn"
    (fact "happy path, processes messages from the channel one by one"
      (processor-thread-fn ..config.. ..db.. (a/to-chan [{:body ..body1.. :receipt-handle ..rh1..}
                                                         {:body ..body2.. :receipt-handle ..rh2..}])) => anything
      (provided
        ..config.. =contains=> {:clair-check-result-queue-url    ..queue-url..
                                :clair-check-result-queue-region ..queue-region..}
        (extract-clair-layer ..body1..) => ..layer1..
        (extract-clair-layer ..body2..) => ..layer2..
        (process-clair-layer ..layer1..) => ..summary1..
        (process-clair-layer ..layer2..) => ..summary2..
        (store-clair-summary ..db.. ..summary1..) => anything
        (store-clair-summary ..db.. ..summary2..) => anything
        (sqs/delete-message {:endpoint ..queue-region..} :queue-url ..queue-url.. :receipt-handle ..rh1..) => anything
        (sqs/delete-message {:endpoint ..queue-region..} :queue-url ..queue-url.. :receipt-handle ..rh2..) => anything))
    (fact "message parse error, nothing should be stored, message should be deleted"
      (processor-thread-fn ..config.. ..db.. (a/to-chan [{:body ..body1.. :receipt-handle ..rh1..}])) => anything
      (provided
        ..config.. =contains=> {:clair-check-result-queue-url    ..queue-url..
                                :clair-check-result-queue-region ..queue-region..}
        (extract-clair-layer ..body1..) =throws=> (ex-info "Cannot decode base64gzip message." {})
        (store-clair-summary anything anything) => anything :times 0
        (sqs/delete-message {:endpoint ..queue-region..} :queue-url ..queue-url.. :receipt-handle ..rh1..) => anything))
    (fact "Some other error, nothing should be stored, message should not be deleted"
      (processor-thread-fn ..config.. ..db.. (a/to-chan [{:body ..body1.. :receipt-handle ..rh1..}])) => anything
      (provided
        ..config.. =contains=> {:clair-check-result-queue-url    ..queue-url..
                                :clair-check-result-queue-region ..queue-region..}
        (extract-clair-layer ..body1..) =throws=> (Exception. "Bad thing happened")
        (store-clair-summary anything anything) => anything :times 0
        (sqs/delete-message anything :queue-url anything :receipt-handle anything) => anything :times 0)))

  )

