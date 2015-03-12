(defproject pierone "0.1.0-SNAPSHOT"
            :description "Pier One Docker Registry"
            :url "https://github.com/zalando-stups/pierone"
            :license {:name "Apache License"
                      :url "http://www.apache.org/licenses/"}
            :scm {:url "git@github.com:zalando-stups/pierone.git"}
            :min-lein-version "2.0.0"
            :dependencies [[org.clojure/clojure "1.6.0"]
                           ; lifecycle management
                           [com.stuartsierra/component "0.2.2"]
                           [environ "1.0.0"]
                           ; REST APIs
                           [metosin/compojure-api "0.17.0"]
                           [metosin/ring-http-response "0.5.2"]
                           [metosin/ring-swagger-ui "2.0.17"]
                           ; logging
                           [org.clojure/tools.logging "0.2.4"]
                           [org.slf4j/slf4j-api "1.7.7"]
                           [org.slf4j/jul-to-slf4j "1.7.7"]
                           [org.slf4j/jcl-over-slf4j "1.7.7"]
                           ; amazon aws (if upgrading, also check the joda-time version)
                           [amazonica "0.3.12" :exclusions [joda-time commons-logging]]
                           [joda-time "2.5"]
                           [org.clojure/data.json "0.2.5"]
                           [org.clojure/data.codec "0.1.0"]
                           [com.brweber2/clj-dns "0.0.2"]
                           [commons-net/commons-net "3.3"]]
            :plugins [[lein-environ "1.0.0"]
                      [lein-cloverage "1.0.2"]
                      [lein-kibit "0.0.8"]]
            :aliases {"cloverage" ["with-profile" "test" "cloverage"]}

            :main pierone.system

            :uberjar-name "pierone.jar"
            :profiles {
                       :log {:dependencies [[org.apache.logging.log4j/log4j-core "2.1"]
                                            [org.apache.logging.log4j/log4j-slf4j-impl "2.1"]]}
                       :no-log {:dependencies [[org.slf4j/slf4j-nop "1.7.7"]]}
                       :dev {
                             :repl-options {:init-ns user}
                             :source-paths ["dev"]
                             :dependencies [[org.clojure/tools.namespace "0.2.9"]
                                            [org.clojure/java.classpath "0.2.0"]]}
                       :test [:log {:dependencies [[org.clojars.runa/conjure "2.1.3"]
                                                   [midje "1.6.3"]
                                                   [clj-http "1.0.1"]]}]
                       :repl [:no-log]
                       :uberjar [:log {:aot :all :resource-paths ["swagger-ui"]}]})
