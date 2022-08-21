(ns teleward.processing
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [teleward.captcha :as captcha]
   [teleward.state.api :as state]
   [teleward.telegram :as tg]
   [teleward.template :as template]
   [teleward.time :refer [unix-now]]
   [teleward.util :refer [with-safe-log]]))


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


(defn terminate-user
  [{:as _context
    :keys [telegram
           config]}
   chat-id user-id username]

  (let [ban-mode
        (-> config :processing :ban-mode)]

    (case ban-mode
      :ban
      (with-safe-log
        (tg/ban-user telegram chat-id user-id {:revoke-messages true})
        (log/infof "User banned (captcha timeout), chat-id: %s, user-id: %s, username: %s"
                   chat-id user-id username))

      :restrict
      (with-safe-log
        (tg/restrict-user telegram chat-id user-id tg/chat-permissions-off)
        (log/infof "User restricted (captcha timeout), chat-id: %s, user-id: %s, username: %s"
                   chat-id user-id username))

      :else
      (log/warn "Ban mode is not set!"))))


(defn process-update
  [{:as context
    :keys [config
           telegram
           state
           me]}
   update-entry]

  (let [;; destruct the config vars in advance
        {:keys [language]
         {:keys [user-trail-attempts
                 message-expires
                 solution-threshold]} :polling
         {captcha-style :style} :captcha}
        config

        {:keys [message]}
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
                     member-username :username
                     member-first-name :first_name
                     member-last-name :last_name} member

                    ;; compose the full name
                    member-full-name
                    (str member-first-name
                         (when member-last-name
                           (str " " member-last-name)))]

              ;; except our bot
              :when (not= my-id member-id)]

        ;; mark it as locked
        (state/set-attrs state chat-id member-id
                         {:locked? true
                          :date-joined date
                          :joined-message-id message_id
                          :username (or member-username member-full-name)
                          :attempt 0})

        (log/infof "Locking a new member, chat-id: %s, user-id: %s, username: %s, date: %s"
                   chat-id member-id member-username date)

        ;; send captcha
        (let [[captcha-text captcha-solution]
              (captcha/make-captcha captcha-style)

              template-context
              {:user [:text_mention member-full-name {:user member}]
               :captcha [:code captcha-text]}

              template
              (template/get-captcha-template language)

              {entities :entities
               captcha-message :message}
              (template/render template template-context)

              ;; track the id of the captcha message
              {captcha-message-id :message_id}
              (with-safe-log
                (tg/send-message telegram
                                 chat-id
                                 captcha-message
                                 {:entities entities}))]

          (log/infof "Captcha sent, chat-id: %s, user-id: %s, username: %s, text: %s, solution: %s, message-id: %s"
                     chat-id member-id member-username captcha-text captcha-solution captcha-message-id)

          (state/set-attrs state chat-id member-id
                           {:captcha-text captcha-text
                            :captcha-solution captcha-solution
                            :captcha-message-id captcha-message-id})))

      ;; check left members
      (when left_chat_member
        (let [{member-id :id}
              left_chat_member
              captcha-message-id
              (state/get-attr state chat-id member-id :captcha-message-id)]
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
            (state/get-attrs state chat-id user-id)]

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
                (state/del-attrs state chat-id user-id))

              ;; otherwise, increase the number of attempts. When the attempts
              ;; are over, delete the captcha message and terminate a user. Keep the
              ;; attributes for the next stage.
              (do
                (log/infof "Failed captcha attempt, chat-id: %s, user-id: %s, username: %s, solution: %s"
                           chat-id user-id user-name text)
                (state/inc-attr state chat-id user-id :attempt)
                (let [attempt
                      (state/get-attr state chat-id user-id :attempt)]

                  (when (> attempt user-trail-attempts)

                    (when captcha-message-id
                      (with-safe-log
                        (tg/delete-message telegram chat-id captcha-message-id)))

                    (terminate-user context chat-id user-id user-name)

                    (when joined-message-id
                      (with-safe-log
                        (tg/delete-message telegram chat-id joined-message-id)))))))))))))


(defn process-updates
  [context updates]
  (doseq [update-entry updates]
    (process-update context update-entry)))


(defn process-pending-users
  "
  Cleanup: ban users who haven't solved the captcha in time.
  The `date-joined` attr tracks the date the user did join.
  Delete the captcha message, ban the user, drop the attributes.
  "
  [{:as context
    :keys [telegram
           state
           config]}]

  (let [ban-mode
        (-> config :processing :ban-mode)

        {{:keys [user-trail-period]} :polling}
        config

        unix-expired
        (- (unix-now) user-trail-period)

        triples
        (state/filter-by-attr state :date-joined :< unix-expired)]

    (doseq [[chat-id user-id attrs]
            triples]

      (let [{:keys [username
                    date-joined
                    joined-message-id
                    captcha-message-id]}
            attrs]

        (when captcha-message-id
          (with-safe-log
            (tg/delete-message telegram chat-id captcha-message-id)))

        (terminate-user context chat-id user-id username)

        (when joined-message-id
          (with-safe-log
            (tg/delete-message telegram chat-id joined-message-id)))

        (state/del-attrs state chat-id user-id)))))
