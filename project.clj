(defproject aiko-services-clj "0.0.1"
  :description "Aiko Services on Clojure(script)"
  :url "https://github.com/pdmct/aiko_services_clj"
  :license {:name "Apache License"
            :url  "http://apache.org/licenses/LICENSE-2.0.txt"}
  :min-lein-version "2.5.0"

  :aliases {"kaocha" ["with-profile" "+dev" "run" "-m" "kaocha.runner"]
            "test"   ["version"]}

  :dependencies [[org.clojure/clojure "1.12.0"]
                 [metosin/malli "0.8.9"]
                 [org.clojure/core.async "1.6.681"]
                 [org.clojure/tools.logging "1.3.0"]
                 [jarohen/nomad "0.9.0"]]

  :deploy-repositories [["clojars" {:url           "https://clojars.org/repo"
                                    :username      :env/clojars_user
                                    :password      :env/clojars_token
                                    :sign-releases false}]]

  :jvm-opts
  [;; ignore things not recognized for our Java version instead of
   ;; refusing to start
   "-XX:+IgnoreUnrecognizedVMOptions"
   ;; disable bytecode verification when running in dev so it starts
   ;; slightly faster
   "-Xverify:none"]
  :target-path "target/%s"
  
  :profiles {:dev {:jvm-opts ["-XX:+UnlockDiagnosticVMOptions"
                              "-XX:+DebugNonSafepoints"
                              "-Dclojure.tools.logging.factory=clojure.tools.logging.impl/slf4j-factory"]

                   :injections [(require 'hashp.core)
                                (require 'debux.core)]

                   :source-paths ["src" "dev/src" "local/src"]
                   :dependencies [[philoskim/debux "0.8.2"]                                  
                                  ;;[org.clojure/clojurescript "1.11.60"]
                                  [org.clojure/test.check "1.1.1"]
                                  [expectations/clojure-test "1.2.1"]
                                  [lambdaisland/kaocha "1.91.1392"]
                                  [hashp "0.2.1"]
                                  [nubank/matcher-combinators "3.5.1"]]}})
