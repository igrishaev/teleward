(defproject com.github.igrishaev/teleward "0.1.0-SNAPSHOT"

  :description
  "Captcha bot for Telegram in Clojure + GraalVM"

  :url
  "https://github.com/igrishaev/teleward"

  :plugins
  [[lein-project-version "0.1.0"]]

  :dependencies
  [[org.clojure/clojure "1.11.1"]
   [http-kit "2.6.0"]
   [cheshire "5.10.0"]
   [org.clojure/tools.cli "1.0.206"]
   [medley "1.4.0"]
   [org.clojure/tools.logging "1.2.4"]
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
