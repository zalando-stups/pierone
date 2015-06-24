(defproject org.zalando.stups/pierone "0.6.0"
  :description "Pier One Docker Registry"
  :url "https://github.com/zalando-stups/pierone"
  :license {:name "Apache License"
            :url  "http://www.apache.org/licenses/"}
  :scm {:url "git@github.com:zalando-stups/pierone"}
  :min-lein-version "2.0.0"

  :dependencies [[org.zalando.stups/friboo "0.24.0"]
                 [yesql "0.5.0-rc3"]

                 [org.apache.commons/commons-compress "1.9"]
                 [org.clojure/data.codec "0.1.0"]

                 [amazonica "0.3.24"]]

  :main ^:skip-aot org.zalando.stups.pierone.core
  :uberjar-name "pierone.jar"

  :plugins [[lein-cloverage "1.0.3"]
            [io.sarnowski/lein-docker "1.1.0"]
            [org.zalando.stups/lein-scm-source "0.2.0"]]

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

  :profiles {:uberjar {:aot :all}

             :test    {:dependencies [[clj-http-lite "0.2.1"]
                                      [org.clojure/java.jdbc "0.3.7"]]}

             :dev     {:repl-options {:init-ns user}
                       :source-paths ["dev"]
                       :dependencies [[org.clojure/tools.namespace "0.2.10"]
                                      [org.clojure/java.classpath "0.2.2"]
                                      [clj-http-lite "0.2.1"]
                                      [org.clojure/java.jdbc "0.3.7"]]}})
