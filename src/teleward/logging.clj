(ns teleward.logging
  "https://stackoverflow.com/questions/16910955/programmatically-configure-logback-appender"
  (:require
   [clojure.tools.logging :as log])
  (:import
   org.slf4j.LoggerFactory
   ch.qos.logback.classic.Level
   ch.qos.logback.classic.Logger
   ch.qos.logback.classic.LoggerContext
   ch.qos.logback.classic.encoder.PatternLayoutEncoder
   ch.qos.logback.core.ConsoleAppender
   ch.qos.logback.core.rolling.RollingFileAppender))


(defn init-logging [& [config]]

  (let [log-context
        (LoggerFactory/getILoggerFactory)

        log-encoder
        (doto (new PatternLayoutEncoder)
          (.setContext log-context)
          (.setPattern "%-12date{YYYY-MM-dd HH:mm:ss.SSS} %-5level - %msg%n")
          (.start))

        app-console
        (doto (new ConsoleAppender)
          (.setContext log-context)
          (.setName "console")
          (.setEncoder log-encoder)
          (.start))

        ^Logger logger
        (.getLogger log-context Logger/ROOT_LOGGER_NAME)]

    (doto logger
      (.detachAndStopAllAppenders)
      (.setAdditive false)
      (.setLevel Level/INFO)
      (.addAppender app-console))))


;;
;; Dev
;;

#_
(

 (require
  '[clojure.tools.logging :as log])

 (log/info "hello")

 )
