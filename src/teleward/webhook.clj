(ns teleward.webhook
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [org.httpkit.server :as server]
   [teleward.processing :as processing]
   [teleward.state :as state]
   [teleward.telegram :as tg])
  (:import
   java.util.Timer
   java.util.TimerTask))


(defn handle-request [request config state me]

  (let [update-entry
        (some-> request
                :body
                io/reader
                (json/parse-stream keyword))]

    (log/debugf "Incoming update: %s"
                (when update-entry
                  (json/generate-string update-entry {:pretty true})))

    (when update-entry
      (processing/process-update config state update-entry me)))

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
            (log/debugf "Running cron job, every: %s" every-ms)
            (processing/process-pending-users config state)))

        result
        (.scheduleAtFixedRate timer timer-task every-ms every-ms)]

    (log/infof "Cron timer has been started, every: %s" every-ms)

    result))


(defn run-server [config state me]

  (let [server-config
        (-> config :webhook :server)

        {:keys [port host]}
        server-config

        webhook-path
        (-> config :webhook :path)

        router
        (fn [request]

          (let [{:keys [uri
                        content-type
                        content-length
                        request-method]}
                request]

            (log/debugf "HTTP request: %s %s %s %s"
                        request-method uri content-type content-length)

            (cond

              (and (= :post request-method)
                   (= webhook-path uri))
              (handle-request request config state me)

              :else
              {:status 404 :body "not found"})))

        server
        (server/run-server router server-config)]

    (log/infof "HTTP server has been started, host: %s, port: %s"
               host port)

    server))


(defn run-webhook [config]
  (let [{:keys [telegram]}
        config

        me
        (tg/get-me telegram)

        state
        (state/make-state)]

    (run-cron-task config state me)
    (run-server config state me)))
