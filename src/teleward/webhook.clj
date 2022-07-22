(ns teleward.webhook
  (:require
   [clojure.java.io :as io]
   [cheshire.core :as json]
   [teleward.telegram :as tg]
   [teleward.processing :as processing]
   [org.httpkit.server :as server])
  (:import
   java.util.Timer
   java.util.TimerTask))


(defn handler [request config state me]
  (when-let [update-entry
             (some-> request
                     :body
                     io/reader
                     json/parse-stream)]
    (processing/process-update config state update-entry me))
  {:status 200 :body "OK"})


(defn run-cron-task [config state me]

  (let [timer
        (new Timer "teleward" false)

        ^Long every-ms
        (-> config
            :polling
            :user-trail-period
            (* 1000))

        timer-task
        (proxy [TimerTask] []
          (run []
            (processing/process-pending-users config state)))]

    (.scheduleAtFixedRate timer timer-task every-ms every-ms)))


(defn run-server [config state me]

  (let [server-config
        {}

        webhook-path
        (-> config :webhook :path)

        app
        (fn [{:as request :keys [uri]}]
          (cond
            (= webhook-path uri)
            (handler request config state me)
            :else
            {:status 404 :body "not found"}))]

    (server/run-server app server-config)))


(defn run-webhook [config]
  (let [{:keys [telegram]}
        config

        me
        (tg/get-me telegram)

        state
        (atom {})]

    (run-cron-task config state me)
    (run-server config state me)))
