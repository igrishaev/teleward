(ns teleward.time)

(defn unix-now []
  (quot (System/currentTimeMillis) 1000))
