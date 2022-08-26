(ns teleward.main
  "
  The main entry point of the program.
  "
  (:gen-class)
  (:require
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.tools.logging :as log]
   [teleward.config :as config]
   [teleward.logging :as logging]
   [teleward.poll :as poll]
   [teleward.version :as version]
   [teleward.webhook :as webhook]))


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
$> teleward -t <telegram-token> -l error --language ru --captcha.style lisp
")

(defn handle-help [summary]
  (println HELP)
  (println summary))


(defn start-work [cli-options]
  (let [overrides
        (config/cli-options->config cli-options)

        config
        (config/make-config overrides)

        {:keys [mode]}
        cli-options]

    (config/validate-config! config)
    (logging/init-logging (:logging config))

    (case mode

      (:polling "polling")
      (poll/run-polling config)

      (:webhook "webhook")
      (webhook/run-webhook config)

      ;; else
      (throw (ex-info "Mode not set" {:exit/code 1})))))


(defn -main*
  "
  An unsafe version of the main function.
  Takes arguments as a single collection (not variadic & args).
  "
  [args]

  (let [cli-spec
        (config/make-cli-opts)

        opts-parsed
        (parse-opts args cli-spec)

        {:keys [options _arguments errors summary]}
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
      (start-work options))))


(defn -main
  "
  A protected version of main that catches any exception.
  If an exception has `:exit/code` value in its ex-data,
  we consider it as expected one and show just ex-message.
  Otherwise, log the full exception.
  "
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
