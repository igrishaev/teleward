(ns teleward.poll
  (:require
   [teleward.captcha :as captcha]
   [teleward.telegram :as tg]
   [teleward.locale :as locale]
   [cheshire.core :as json]
   [clojure.string :as str]
   [clojure.tools.logging :as log]))


(defmacro with-safe-log [& body]
  `(try
     ~@body
     (catch Throwable e#
       (log/error (ex-message e#)))))

(defn unix-now []
  (quot (System/currentTimeMillis) 1000))

(defn set-attr [state chat-id user-id attr val]
  (swap! state assoc-in [chat-id user-id attr] val))

(defn get-attr [state chat-id user-id attr]
  (get-in @state [chat-id user-id attr]))

(defn del-attr [state chat-id user-id attr]
  (swap! state update-in [chat-id user-id] dissoc attr))

(defn inc-attr [state chat-id user-id attr]
  (swap! state update-in [chat-id user-id attr] (fnil inc 0)))

(defn set-attrs [state chat-id user-id mapping]
  (swap! state update-in [chat-id user-id] merge mapping))

(defn get-attrs [state chat-id user-id]
  (get-in @state [chat-id user-id]))

(defn del-attrs [state chat-id user-id]
  (swap! state update chat-id dissoc user-id))


(defn looks-solution? [text len-threshold]
  (and
   (not (str/blank? text))
   (<= (count text) len-threshold)))


(defn almost-now [time offset]
  (let [now (unix-now)]
    (<= (- now offset) time (+ now offset))))


(defn process-updates
  [config state updates]

  (let [{:keys [lang
                telegram]
         {:keys [user-trail-attempts
                 message-expires
                 solution-threshold]} :polling
         {captcha-style :style} :captcha}
        config]

    (doseq [upd-entry updates

            :let
            [{:keys [message
                     update_id]}
             upd-entry

             {:keys [chat
                     from
                     date
                     text
                     message_id
                     new_chat_members
                     left_chat_member]}
             message

             {chat-id :id}
             chat

             {user-id :id}
             from]

            :when
            (and message
                 (almost-now date message-expires))]

      ;; for each new member...
      (doseq [member new_chat_members
              :let [{member-id :id
                     member-username :username} member]]

        ;; mark it as locked
        (set-attrs state chat-id member-id
                   {:locked? true :date date})

        ;; send captcha
        (let [[captcha-text captcha-solution]
              (captcha/make-captcha captcha-style)

              captcha-message
              (locale/get-captcha-message lang member-username captcha-text)

              {captcha-message-id :message_id}
              (with-safe-log
                (tg/send-message telegram chat-id captcha-message))]

          (set-attrs state chat-id member-id
                     {:captcha-text captcha-text
                      :captcha-solution captcha-solution
                      :captcha-message-id captcha-message-id})))

      ;; check left members
      (when left_chat_member
        (let [{member-id :id}
              left_chat_member
              captcha-message-id
              (get-attr state chat-id member-id :captcha-message-id)]
          ;; delete captcha message
          (when captcha-message-id
            (with-safe-log
              (tg/delete-message telegram chat-id captcha-message-id)))
          ;; cheanup state
          (del-attrs state chat-id member-id)))

      ;; check for commands
      (when (= text "/health")
        (with-safe-log
          (tg/send-message telegram chat-id "OK")))

      ;; check the current message
      (let [{:keys [locked?
                    captcha-text
                    captcha-solution
                    captcha-message-id]}
            (get-attrs state chat-id user-id)]

        (when (and locked?
                   captcha-text
                   captcha-solution)

          (with-safe-log
            (tg/delete-message telegram chat-id message_id))

          (if (and (looks-solution? text solution-threshold)
                   (= (str/trim text) captcha-solution))

            (do
              (when captcha-message-id
                (with-safe-log
                  (tg/delete-message telegram chat-id captcha-message-id)))
              (del-attrs state chat-id user-id))

            (do
              (inc-attr state chat-id user-id :attempt)
              (let [attempt
                    (get-attr state chat-id user-id :attempt)]
                (when (> attempt user-trail-attempts)
                  (with-safe-log
                    (tg/ban-user telegram chat-id user-id
                                 {:revoke-messages true}))
                  (del-attrs state chat-id user-id))))))))))


(defn process-pending-users [config state]
  (let [{:keys [telegram]
         {:keys [user-trail-period]} :polling}
        config]
    (doseq [[chat-id user->attrs] @state
            [user-id {:keys [date]}] user->attrs]
      (when (and date (> (- (unix-now) date) user-trail-period))
        (when-let [captcha-message-id
                   (get-attr state chat-id user-id :captcha-message-id)]
          (with-safe-log
            (tg/delete-message telegram chat-id captcha-message-id)))
        (with-safe-log
          (tg/ban-user telegram chat-id user-id {:revoke-messages true}))
        (del-attrs state chat-id user-id)))))


(defn save-offset [offset-file offset]
  (spit offset-file (str offset)))

(defn load-offset [offset-file]
  (try
    (-> offset-file slurp Long/parseLong)
    (catch Throwable _
      nil)))


(defn run-poll
  [config]

  (let [{{:keys [udpate-timeout
                 offset-file]} :polling
         :keys [telegram]}
        config

        state
        (atom {})

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

        (process-updates config state updates)
        (process-pending-users config state)

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
