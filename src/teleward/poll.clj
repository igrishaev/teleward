(ns teleward.poll
  (:require
   [teleward.telegram :as tg]
   [cheshire.core :as json]
   [teleward.state :as state]
   [teleward.util :refer [with-safe-log]]
   [teleward.processing :as processing]
   [clojure.tools.logging :as log]))


(defn save-offset [offset-file offset]
  (spit offset-file (str offset)))


(defn load-offset [offset-file]
  (try
    (-> offset-file slurp Long/parseLong)
    (catch Throwable _
      nil)))


(defn run-polling
  [config]

  (let [{{:keys [udpate-timeout
                 offset-file]} :polling
         :keys [telegram]}
        config

        me
        (tg/get-me telegram)

        state
        (state/make-state)

        offset
        (load-offset offset-file)]

    (loop [offset offset]

      (let [updates
            (with-safe-log
              (tg/get-updates telegram
                              {:offset offset
                               :timeout udpate-timeout}))

            new-offset
            (or (some-> updates peek :update_id inc)
                offset)]

        (log/debugf "Got %s updates, next offset: %s, updates: %s"
                    (count updates)
                    new-offset
                    (json/generate-string updates {:pretty true}))

        (when offset
          (save-offset offset-file new-offset))

        (processing/process-updates config state updates me)
        (processing/process-pending-users config state)

        (recur new-offset)))))


#_
(

 (def -telegram
   {:token "..."
    :user-agent "Clojure 1.10.3"
    :timeout (* 65 1000)
    :keepalive (* 65 1000)})

 (def -config {:telegram -telegram})

 (run-poll -config)

 )
