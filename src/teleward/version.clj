(ns teleward.version
  "
  Get the current version baked into the jar
  with the `lein-project-version` plugin.
  "
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))


(defn get-version []
  (some-> "VERSION"
          (io/resource)
          (slurp)
          (str/trim)))
