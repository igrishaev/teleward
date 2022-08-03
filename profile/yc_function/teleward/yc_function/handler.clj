(ns teleward.yc-function.handler
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io])
  (:import
   javax.servlet.http.HttpServletRequest
   javax.servlet.http.HttpServletResponse)
  (:gen-class
   :extends javax.servlet.http.HttpServlet
   :main false
   :name teleward.YCHandler))


(defn -doPost
  [this
   ^HttpServletRequest request
   ^HttpServletResponse response]

  (let [content-type
        (.getContentType request)

        content-length
        (or (.getContentLength request) 0)

        uri
        (.getRequestURI request)

        in-stream
        (.getInputStream request)

        out-stream
        (.getOutputStream response)

        data
        (-> in-stream io/reader json/parse-stream)]

    (.setContentType response "application/json")

    (.print out-stream
            (json/generate-string {:echo data} {:pretty true}))))


#_(
   (compile 'teleward.yc-function.handler)
   (import 'teleward.YCHandler)
   )
