(ns teleward.config
  (:require
   [clojure.string :as str]
   [medley.core :refer [deep-merge]]))


(def defaults
  {:telegram
   {:user-agent "Clojure"
    :timeout (* 65 1000)
    :keepalive (* 65 1000)}

   :lang :ru

   :mode :polling

   :logging
   {:level :info
    :pattern "%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n"
    :console true
    :file "logs/teleward.log"}

   :captcha
   {:style :lisp}

   :polling
   {:offset-file "TELEGRAM_OFFSET"
    :udpate-timeout 60
    :message-expires (* 60 2)
    :user-trail-period 60
    :user-trail-attempts 3
    :solution-threshold 5}

   :webhook
   {:path "/telegram/webhook"
    :server {:host "localhost"
             :port 8090}}})


(def cli-opts
  [["-v" "--version"]
   ["-h" "--help"]

   ["-t" "--telegram.token TOKEN" "Telegram token"]

   ["-l" "--logging.level LEVEL" "Logging level"
    :default (get-in defaults [:logging :level])
    :parse-fn keyword]

   ["-m" "--mode MODE" "Working mode: polling or webhook"
    :default (get-in defaults [:mode])
    :parse-fn keyword]

   [nil "--webhook.path PATH" "Path for webhook handler"
    :default (get-in defaults [:webhook :path])]

   [nil "--webhook.server.host HOST" "Host to listen in webhook mode"
    :default (get-in defaults [:webhook :server :host])]

   ["-p" "--webhook.server.port PORT" "Port to listen in webhook mode"
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

   [nil "--lang LANG" "Message language"
    :default (get-in defaults [:lang])
    :parse-fn keyword]

   [nil "--captcha.style CAPTCHA_STYLE" "Captcha style"
    :default (get-in defaults [:captcha :style])
    :parse-fn keyword]])


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


(defn from-env []
  {:telegram
   {:token (System/getenv "TELEGRAM_TOKEN")}})


(defn make-config [& [cli-options]]
  (deep-merge defaults
              (from-env)
              (cli-options->config cli-options)))


(defn validate-config! [config]
  (when-not (get-in config [:telegram :token])
    (throw (ex-info "Telegram token not set" {:exit/code 1}))))
