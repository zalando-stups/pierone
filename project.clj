(defproject pierone "0.1.0-SNAPSHOT"
            :description "Pier One Docker Registry"
            :url "https://github.com/zalando-stups/pierone"
            :license {:name "Apache License"
                      :url "http://www.apache.org/licenses/"}
            :scm {:url "git@github.com:zalando-stups/pierone.git"}
            :min-lein-version "2.0.0"
            :dependencies [[org.clojure/clojure "1.6.0"]
                           [ring "1.3.2"]
                           [io.sarnowski/swagger1st "0.3.0"]
                           [ring/ring-json "0.3.1"]
                           [environ "1.0.0"]
                           [commons-io/commons-io "2.4"]]
            :plugins [[lein-cloverage "1.0.2"]
                      [lein-ring "0.9.2"]]

            :aliases {"cloverage" ["with-profile" "test" "cloverage"]}

            :ring {:handler pierone.core/app}

            :uberjar-name "pierone.jar"

            :profiles {:test {:dependencies [[ring/ring-mock "0.2.0"]]}})

