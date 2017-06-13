(defproject org.zalando.stups/pierone "1.8.5"
  :description "Pier One Docker Registry"
  :url "https://github.com/zalando-stups/pierone"
  :license {:name "Apache License"
            :url  "http://www.apache.org/licenses/"}
  :scm {:url "git@github.com:zalando-stups/pierone"}
  :min-lein-version "2.0.0"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.zalando.stups/friboo "1.10.0"]
                 [org.zalando.stups/tokens "0.11.0-beta-2"]
                 [yesql "0.5.1"]
                 [cheshire "5.6.0"]
                 [digest "1.4.4"]
                 [org.apache.commons/commons-compress "1.10"]
                 [org.clojure/data.codec "0.1.0"]
                 [amazonica "0.3.57"]
                 [org.clojure/java.jdbc "0.4.1"]
                 [org.clojure/core.async "0.2.374"]]

  :main ^:skip-aot org.zalando.stups.pierone.core
  :uberjar-name "pierone.jar"

  :plugins [[lein-cloverage "1.0.6"]
            [io.sarnowski/lein-docker "1.1.0"]
            [org.zalando.stups/lein-scm-source "0.2.0"]
            [lein-set-version "0.4.1"]]

  :docker {:image-name #=(eval (str (some-> (System/getenv "DEFAULT_DOCKER_REGISTRY")
                                            (str "/"))
                                    "stups/pierone"))}

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["clean"]
                  ["uberjar"]
                  ["scm-source"]
                  ["docker" "build"]
                  ["docker" "push"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :aliases {"cloverage" ["with-profile" "test" "cloverage"]}

  :test-selectors {:default     :integration
                   :unit        :unit
                   :integration :integration}

  :profiles {:uberjar {:aot :all}

             :dev     {:repl-options {:init-ns user}
                       :source-paths ["dev"]
                       :jvm-opts ["-Dhystrix.threadpool.default.coreSize=50"]
                       :dependencies [[org.clojure/tools.namespace "0.2.10"]
                                      [org.clojure/java.classpath "0.2.2"]
                                      [clj-http "2.0.0"]
                                      [midje "1.8.3"]]}})
