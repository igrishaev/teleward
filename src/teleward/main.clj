(ns teleward.main
  (:gen-class)
  (:require
   [teleward.version :as version]
   [teleward.logging :as logging]
   [teleward.poll :as poll]
   [teleward.config :as config]
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

      ;; help
      ;; (println "help")

      :else
      (start-polling options))))


(defn -main
  [& args]
  (try
    (-main* args)
    (catch Throwable e

      (let [{:exit/keys [code message]}
            (ex-data e)

            code
            (or code 1)

            message
            (or message (ex-message e))

            out
            (if (zero? code) *out* *err*)]

        (binding [*out* out]
          (println message)
          (System/exit code))))))
