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

   :logging
   {:level :info
    ;; :pattern
    ;; :file



    ;; :encoder :pattern
    ;; :pattern "%p [%d] %t - %c %m%n"

    ;; :console "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

    :console true

    ;; :file {:file "..."}
    ;; :overrides {"foo.bar.baz" :debug}
    }

   :captcha
   {:style "clojure"
    :vars-count 2}

   :polling
   {:offset-file "TELEGRAM_OFFSET"
    :udpate-timeout 60
    :message-expires 60
    :user-trail-period 60
    :user-trail-attempts 3
    :solution-threshold 5}})


(def cli-opts
  [["-v" "--version"]
   ["-h" "--help"]

   ["-t" "--telegram.token TOKEN" "Telegram token"]

   ["-l" "--logging.level LEVEL" "Logging level"
    :default (get-in defaults [:logging :level])
    :parse-fn keyword]])


(defn kw->path [kw]
  (mapv keyword
        (-> kw name (str/split #"\."))))


(defn cli-options->config [options]
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
