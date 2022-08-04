(ns teleward.yc-function.handler
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [medley.core :refer [deep-merge]]
   [teleward.config :as config]
   [teleward.logging :as logging]
   [teleward.processing :as processing]
   [teleward.yc-function.state :as state]
   [teleward.telegram :as tg])
  (:import
   javax.servlet.http.HttpServletRequest
   javax.servlet.http.HttpServletResponse)
  (:gen-class
   :extends javax.servlet.http.HttpServlet
   :main false
   :name teleward.YCHandler))


(defn json-request?
  [^HttpServletRequest request]
  (some-> request
          .getContentType
          str/lower-case
          (str/includes? "application/json")))


(defn parse-json
  [^HttpServletRequest request]
  (-> request
      .getInputStream
      io/reader
      json/parse-stream))


(def config-overrides
  {:logging {:file nil}})


(def INIT
  (delay
    (let [config
          (-> nil
              config/make-config
              config/validate-config!
              (deep-merge config-overrides))

          {:keys [logging
                  telegram]}
          config

          me
          (tg/get-me telegram)

          state
          (state/make-state)]

      (logging/init-logging logging)

      {:me me
       :state state
       :config config})))


(defn -service
  [_this
   ^HttpServletRequest request
   ^HttpServletResponse response]

  (let [method
        (.getMethod request)

        uri
        (.getContextPath request)

        content-type
        (.getContentType request)

        content-length
        (.getContentLength request)

        _
        (log/infof "Incoming HTTP request, method: %s, path: %s, type: %s, len: %s"
                   method uri content-type content-length)

        {:keys [state me config]}
        @INIT

        webhook-path
        (-> config :webhook :path)]

    (if (and (= method "POST")
             (= uri webhook-path)
             (json-request? request))

      (let [data (parse-json request)]
        (log/debugf "Payload: %s" (json/generate-string data {:pretty true}))

        (try
          (processing/process-update config state data)
          (processing/process-pending-users config state)
          (.setStatus response 200)

          (catch Throwable e
            (log/errorf e "Unhandled exception")
            (.sendError response 500 "Server Error"))))

      (.sendError response 404 "Not found"))))


#_(
   (compile 'teleward.yc-function.handler)
   (import 'teleward.YCHandler)
   )
