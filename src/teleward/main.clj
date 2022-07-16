(ns teleward.main
  (:gen-class)
  (:require
   [teleward.version :as version]
   [teleward.logging :as logging]
   [teleward.poll :as poll]
   [teleward.config :as config]
   [clojure.tools.logging :as log]
   [clojure.tools.cli :refer [parse-opts]]))


(defn handle-errors [errors]
  (let [message
        (with-out-str
          (println "Some of the arguments are incorrect or unknown:")
          (println)
          (doseq [error errors]
            (println " -" error)))]
    (throw (ex-info message {:type :cli/error
                             :exit/code 1
                             :exit/message message}))))


(def HELP "
Teleward bot. Usage:
$> teleward -t <telegram-token> -l error --lang ru --captcha.style lisp
")

(defn handle-help [summary]
  (println HELP)
  (println summary))


(defn start-polling [cli-options]
  (let [config
        (config/make-config cli-options)]
    (config/validate-config! config)
    (logging/init-logging (:logging config))
    (poll/run-poll config)))


(defn -main* [args]

  (let [opts-parsed
        (parse-opts args config/cli-opts)

        {:keys [options arguments errors summary]}
        opts-parsed

        {:keys [help version]}
        options]


    (cond

      errors
      (handle-errors errors)

      version
      (println (version/get-version))

      help
      (handle-help summary)

      :else
      (start-polling options))))


(defn -main
  [& args]
  (try
    (-main* args)
    (catch Throwable e

      (let [ex-context
            (ex-data e)

            {exit-code :exit/code}
            ex-context

            expected?
            (some? exit-code)

            final-code
            (or exit-code 1)

            out
            (if (zero? final-code) *out* *err*)]

        (binding [*out* out]
          (println (ex-message e)))

        (when-not expected?
          (log/errorf e "Unhandled exception, context: %s" ex-context))

        (System/exit final-code)))))
