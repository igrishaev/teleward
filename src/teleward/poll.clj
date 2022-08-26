(ns teleward.poll
  (:require
   [cheshire.core :as json]
   [clojure.tools.logging :as log]
   [teleward.processing :as processing]
   [teleward.state-atom :as state-atom]
   [teleward.telegram :as tg]
   [teleward.util :refer [with-safe-log]]))


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
        (state-atom/make-state)

        context
        {:me me
         :state state
         :telegram telegram
         :config config}

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

        (processing/process-updates context updates)
        (processing/process-pending-users context)

        (recur new-offset)))))


#_
(

 (def -telegram
   {:token "..."
    :user-agent "Clojure 1.10.3"
    :timeout (* 65 1000)
    :keepalive (* 65 1000)})

 (def -config {:telegram -telegram})

 (run-polling -config)

 )
