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


(defn -doPost
  [_this
   ^HttpServletRequest request
   ^HttpServletResponse response]

  (log/infof "Incoming POST request, path: %s, type: %s, len: %s"
             (.getContextPath request)
             (.getContentType request)
             (.getContentLength request))

  (let [{:keys [state me config]}
        @INIT

        data
        (when (json-request? request)
          (-> request
              .getInputStream
              io/reader
              json/parse-stream))]

    (log/debugf "Payload: %s" (json/generate-string data {:pretty true}))

    (when data
      (processing/process-update config state data me))

    (.setContentType response "text/plain")
    (-> response .getOutputStream (.print "OK"))))


#_(
   (compile 'teleward.yc-function.handler)
   (import 'teleward.YCHandler)
   )
