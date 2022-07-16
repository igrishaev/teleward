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
   ch.qos.logback.core.FileAppender))


(defn kw->level ^Level [kw]
  (case kw
    :debug Level/DEBUG
    :info Level/INFO
    :error Level/ERROR
    (throw (ex-info (format "Wrong logging level: %s" kw)
                    {:level kw}))))

(defn init-logging [logging]

  (let [{:keys [level
                pattern
                console
                file]}
        logging

        log-context
        (LoggerFactory/getILoggerFactory)

        log-encoder
        (doto (new PatternLayoutEncoder)
          (.setContext log-context)
          (.setPattern pattern)
          (.start))

        ^Logger root-logger
        (.getLogger log-context Logger/ROOT_LOGGER_NAME)]

    (doto root-logger
      (.detachAndStopAllAppenders)
      (.setAdditive false)
      (.setLevel (kw->level level)))

    (when console
      (let [app-console
            (doto (new ConsoleAppender)
              (.setContext log-context)
              (.setName "console")
              (.setEncoder log-encoder)
              (.start))]
        (.addAppender root-logger app-console)))

    (when file
      (let [app-file
            (doto (new FileAppender)
              (.setContext log-context)
              (.setName "file")
              (.setEncoder log-encoder)
              (.setAppend true)
              (.setFile file)
              (.start))]
        (.addAppender root-logger app-file)))

    root-logger))


;;
;; Dev
;;

#_
(

 (require
  '[clojure.tools.logging :as log])

 (def -logging
   {:level :debug
    :pattern "%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n"
    :console true
    :file "logs/teleward.log"})

 (init-logging -logging)

 (log/info "hello")

 )
