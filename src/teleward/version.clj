(ns teleward.version
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))


(defn get-version []
  (some-> "VERSION"
          (io/resource)
          (slurp)
          (str/trim)))
