(ns teleward.config
  (:require
   [clojure.string :as str]
   [cprop.core :as cprop]))


(defn make-cli-opts []

  (let [defaults
        (cprop/load-config)]

    [["-v" "--version"]
     ["-h" "--help"]

     ["-t" "--telegram.token TOKEN"
      "Telegram token"]

     ["-l" "--logging.level LEVEL"
      "Logging level"
      :default (get-in defaults [:logging :level])]

     ["-m" "--mode MODE"
      "Working mode: polling or webhook"
      :default (get-in defaults [:mode])]

     [nil "--processing.ban-mode BAN_MODE"
      "What to do with users who didn't solve captcha in time (ban, restrict)"
      :default (get-in defaults [:processing :ban-mode])]

     [nil "--webhook.path PATH"
      "Path for webhook handler"
      :default (get-in defaults [:webhook :path])]

     [nil "--webhook.server.host HOST"
      "Host to listen in webhook mode"
      :default (get-in defaults [:webhook :server :host])]

     ["-p" "--webhook.server.port PORT"
      "Port to listen in webhook mode"
      :default (get-in defaults [:webhook :server :port])
      :parse-fn parse-long]

     [nil "--polling.user-trail-period TRIAL_PERIOD"
      "How many seconds to wait before banning a user who hasn't solved captcha in time."
      :default (get-in defaults [:polling :user-trail-period])
      :parse-fn parse-long]

     [nil "--polling.user-trail-attempts TRIAL_ATTEMPTS"
      "How many attempts a user has to solve captcha."
      :default (get-in defaults [:polling :user-trail-attempts])
      :parse-fn parse-long]

     [nil "--telegram.offset-file OFFSET_FILE"
      "Path to a file where Telegram stores pollling offset."
      :default (get-in defaults [:polling :offset-file])]

     [nil "--language LANGUAGE"
      "Language of messages and captcha: en, ru"
      :default (get-in defaults [:language])]

     [nil "--captcha.style CAPTCHA_STYLE"
      "Captcha style: lisp or enything else."
      :default (get-in defaults [:captcha :style])]]))


(defn kw->path
  "
  :foo.bar => [:foo :bar]
  "
  [kw]
  (mapv keyword
        (-> kw name (str/split #"\."))))


(defn cli-options->config
  "
  Turn CLI parsed options into a nested map.
  "
  [options]
  (reduce-kv
   (fn [result k v]
     (assoc-in result (kw->path k) v))
   {}
   options))


(defn make-config [& [overrides]]
  (cprop/load-config :merge [overrides]))


(defn validate-config! [config]
  (when-not (get-in config [:telegram :token])
    (throw (ex-info "Telegram token not set" {:exit/code 1})))
  config)
