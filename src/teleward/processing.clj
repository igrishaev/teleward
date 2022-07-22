(ns teleward.processing
  (:require
   [teleward.captcha :as captcha]
   [teleward.telegram :as tg]
   [teleward.locale :as locale]
   [cheshire.core :as json]
   [clojure.string :as str]
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


(defn unix-now []
  (quot (System/currentTimeMillis) 1000))


;;
;; A bunch of functions to track the state of the world.
;; The content of the state atom is a map like this:
;; chat-id => user => {attrs}
;; The functions provide CRUD operations for a single attribute
;; and multiple attributes as well (with -s at the end).
;;

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

;;

(defn looks-solution?
  "
  True if a message *might be* (but not for sure)
  a solution for captcha (pretty short).
  "
  [text len-threshold]
  (and
   (not (str/blank? text))
   (<= (count text) len-threshold)))


(defn almost-now
  "
  True if the given Unix `time` is around the current Unix timestamp.
  "
  [time offset]
  (let [now (unix-now)]
    (<= (- now offset) time (+ now offset))))


(defn process-update
  [config state update-entry me]

  (let [;; destruct the config vars in advance
        {:keys [lang
                telegram]
         {:keys [user-trail-attempts
                 message-expires
                 solution-threshold]} :polling
         {captcha-style :style} :captcha}
        config

        {:keys [message
                update_id]}
        update-entry

        {:keys [chat
                from
                date
                text
                ;; forward_date
                ;; forward_sender_name
                message_id
                new_chat_members
                left_chat_member]}
        message

        {my-id :id}
        me

        {chat-id :id}
        chat

        {user-id :id
         user-name :username}
        from]

    ;; process only recent updates
    (when (and message
               (almost-now date message-expires))

      ;; for each new member...
      (doseq [member new_chat_members
              :let [{member-id :id
                     member-username :username} member]
              ;; except our bot
              :when (not= my-id member-id)]

        ;; mark it as locked
        (set-attrs state chat-id member-id
                   {:locked? true
                    :date-joined date
                    :joined-message-id message_id
                    :username user-name})

        (log/infof "Locking a new member, chat-id: %s, user-id: %s, username: %s, date: %s"
                   chat-id member-id member-username date)

        ;; send captcha
        (let [[captcha-text captcha-solution]
              (captcha/make-captcha captcha-style)

              captcha-message
              (locale/get-captcha-message lang member-username captcha-text)

              ;; track the id of the captcha message
              {captcha-message-id :message_id}
              (with-safe-log
                (tg/send-message telegram chat-id captcha-message))]

          (log/infof "Captcha sent, chat-id: %s, user-id: %s, username: %s, text: %s, solution: %s, message-id: %s"
                     chat-id member-id member-username captcha-text captcha-solution captcha-message-id)

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
          ;; just delete captcha message
          (when captcha-message-id
            (with-safe-log
              (tg/delete-message telegram chat-id captcha-message-id)))))

      ;; check for commands
      (when (= text "/health")
        (with-safe-log
          ;; TODO: provide uptime report
          (tg/send-message telegram chat-id "OK")))

      ;; check the current message for captcha
      (let [{:keys [locked?
                    captcha-text
                    captcha-solution
                    captcha-message-id
                    joined-message-id]}
            (get-attrs state chat-id user-id)]

        ;; if the current user is locked...
        (when (and locked?
                   captcha-text
                   captcha-solution)

          ;; ...and the message was not about a newcomer...
          (when-not new_chat_members
            ;; ...log and delete it
            (log/infof "Message from a locked user, chat-id: %s, user-id: %s, username: %s, text: %s"
                       chat-id user-id user-name text)
            (with-safe-log
              (tg/delete-message telegram chat-id message_id)))

          ;; if it was a text message...
          (when text

            ;; ...and it solves the captcha...
            (if (and (looks-solution? text solution-threshold)
                     (= (str/trim text) captcha-solution))

              ;; ...delete the captcha message and reset all the attributes
              (do
                (log/infof "Captcha solved, chat-id: %s, user-id: %s, username: %s, solution: %s"
                           chat-id user-id user-name text)
                (when captcha-message-id
                  (with-safe-log
                    (tg/delete-message telegram chat-id captcha-message-id)))
                (del-attrs state chat-id user-id))

              ;; otherwise, increase the number of attempts. When the attempts
              ;; are over, delete the captcha message and ban a user. Keep the
              ;; attributes for the next stage.
              (do
                (log/infof "Failed captcha attempt, chat-id: %s, user-id: %s, username: %s, solution: %s"
                           chat-id user-id user-name text)
                (inc-attr state chat-id user-id :attempt)
                (let [attempt
                      (get-attr state chat-id user-id :attempt)]
                  (when (> attempt user-trail-attempts)
                    (when captcha-message-id
                      (with-safe-log
                        (tg/delete-message telegram chat-id captcha-message-id)))
                    (log/infof "User banned (captcha attempts), chat-id: %s, user-id: %s, username: %s"
                               chat-id user-id user-name)
                    (with-safe-log
                      (tg/ban-user telegram chat-id user-id
                                   {:revoke-messages true}))
                    (when joined-message-id
                      (with-safe-log
                        (tg/delete-message telegram chat-id joined-message-id)))))))))))))


(defn process-updates
  [config state updates me]
  (doseq [update-entry updates]
    (process-update config state update-entry me)))


(defn process-pending-users
  "
  Cleanup: ban users who haven't solved the captcha in time.
  The `date` attr tracks the date the user did join.
  Delete the captcha message, ban the user, drop the attributes.
  "
  [config state]
  (let [{:keys [telegram]
         {:keys [user-trail-period]} :polling}
        config]
    (doseq [[chat-id user->attrs] @state
            [user-id {:keys [date-joined
                             captcha-message-id
                             joined-message-id
                             username]}] user->attrs]
      (when (and date-joined
                 (> (- (unix-now) date-joined) user-trail-period))
        (when captcha-message-id
          (with-safe-log
            (tg/delete-message telegram chat-id captcha-message-id)))
        (with-safe-log
          (tg/ban-user telegram chat-id user-id {:revoke-messages true}))
        (log/infof "User banned (captcha timeout), chat-id: %s, user-id: %s, username: %s, date joined: %s"
                   chat-id user-id username date-joined)
        (when joined-message-id
          (with-safe-log
            (tg/delete-message telegram chat-id joined-message-id)))
        (del-attrs state chat-id user-id)))))
