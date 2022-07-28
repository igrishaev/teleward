(defproject com.github.igrishaev/teleward "0.1.4-SNAPSHOT"

  :description
  "Captcha bot for Telegram in Clojure + GraalVM"

  :url
  "https://github.com/igrishaev/teleward"

  :deploy-repositories
  {"releases" {:url "https://repo.clojars.org" :creds :gpg}}

  :plugins
  [[lein-project-version "0.1.0"]]

  :license
  {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
   :url "https://www.eclipse.org/legal/epl-2.0/"}

  :release-tasks
  [["vcs" "assert-committed"]
   ["test"]
   ["change" "version" "leiningen.release/bump-version" "release"]
   ["vcs" "commit"]
   ["vcs" "tag" "--no-sign"]
   ["deploy"]
   ["change" "version" "leiningen.release/bump-version"]
   ["vcs" "commit"]
   ["vcs" "push"]]

  :dependencies
  [[org.clojure/clojure "1.11.1"]
   [http-kit "2.6.0"]
   [cheshire "5.10.0"]
   [org.clojure/tools.cli "1.0.206"]
   [medley "1.4.0"]
   [org.clojure/tools.logging "1.2.4"]
   [com.taoensso/faraday "1.11.4"]
   [ch.qos.logback/logback-classic "1.2.11"]]

  :main ^:skip-aot teleward.main

  :target-path "target/%s"

  :uberjar-name "teleward.jar"

  :profiles
  {:dev
   {:global-vars
    {*warn-on-reflection* true
     *assert* true}}

   :uberjar
   {:aot :all
    :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
