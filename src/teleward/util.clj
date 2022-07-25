(ns teleward.util
  (:require
   [clojure.tools.logging :as log]))


(defmacro with-safe-log
  "
  A macro to wrap Telegram calls (prevent the whole program from crushing).
  "
  [& body]
  `(try
     ~@body
     (catch Throwable e#
       (log/error (ex-message e#)))))
