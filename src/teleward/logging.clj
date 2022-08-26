(ns teleward.logging
  "
  Configure Logback manually by a config map.
  An XML file in the `resources` firectory is not an options as:
  1) we need do build the config dynamically, and
  2) it doesn't work with GraalVM/native-image.
  See https://stackoverflow.com/questions/16910955/
  "
  (:import
   ch.qos.logback.classic.Level
   ch.qos.logback.classic.Logger
   ch.qos.logback.classic.encoder.PatternLayoutEncoder
   ch.qos.logback.core.ConsoleAppender
   ch.qos.logback.core.FileAppender
   org.slf4j.LoggerFactory))


(defn kw->level ^Level [kw]
  (case kw
    (:debug "debug") Level/DEBUG
    (:info "info") Level/INFO
    (:error "error") Level/ERROR
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
      (let [{console-name :name
             console-target :target
             :or {console-name "console"
                  console-target "out"}}
            console

            Target
            (case console-target
              (:out "out" "System.out") "System.out"
              (:err "err" "System.err") "System.err"
              (throw (new Exception "wrong console target")))

            app-console
            (doto (new ConsoleAppender)
              (.setContext log-context)
              (.setName console-name)
              (.setTarget Target)
              (.setEncoder log-encoder)
              (.start))]
        (.addAppender root-logger app-console)))

    ;; just a plain FileAppender for now although a rolling file would be better
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
