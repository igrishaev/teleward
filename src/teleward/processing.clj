(ns teleward.processing
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [teleward.captcha :as captcha]
   [teleward.state :as state]
   [teleward.telegram :as tg]
   [teleward.template :as template]
   [teleward.time :refer [unix-now]]
   [teleward.util :refer [with-safe-log]]))


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
      (:ban "ban")
      (with-safe-log
        (tg/ban-user telegram chat-id user-id {:revoke-messages true})
        (log/infof "User banned (captcha timeout), chat-id: %s, user-id: %s, username: %s"
                   chat-id user-id username))

      (:restrict "restrict")
      (with-safe-log
        (tg/restrict-user telegram chat-id user-id tg/chat-permissions-off)
        (log/infof "User restricted (captcha timeout), chat-id: %s, user-id: %s, username: %s"
                   chat-id user-id username))

      ;; else
      (log/warn "Ban mode is not set!"))))


(defn delete-message [{:keys [telegram]} chat-id message-id]
  (when message-id
    (with-safe-log
      (tg/delete-message telegram chat-id message-id))))


(defn get-full-name [user]
  (let [{:keys [first_name
                last_name]}
        user]
    (str first_name
         (when last_name
           (str " " last_name)))))


(defn process-new-member
  [context message member]

  (let [{:keys [state config telegram]}
        context

        {:keys [language]
         {captcha-style :style} :captcha}
        config

        {:keys [date
                chat
                message_id]}
        message

        {chat-id :id}
        chat

        {member-id :id
         member-username :username
         member-first-name :first_name
         member-last-name :last_name}
        member

        member-full-name
        (str member-first-name
             (when member-last-name
               (str " " member-last-name)))]

    (state/set-attrs state chat-id member-id
                     {:locked? true
                      :date-joined date
                      :joined-message-id message_id
                      :username (or member-username member-full-name)
                      :attempt 0})

    (log/infof "Locking a new member, chat-id: %s, user-id: %s, username: %s, date: %s"
               chat-id member-id member-username date)

    (let [[captcha-text captcha-solution]
          (captcha/make-captcha captcha-style)

          template-context
          {:user (template/user-mention member)
           :captcha [:code captcha-text]}

          template
          (template/get-captcha-template language)

          {entities :entities
           captcha-message :message}
          (template/render template template-context)

          buttons
          (captcha/gen-buttons captcha-solution 99 3 2)

          ;; track the id of the captcha message
          {captcha-message-id :message_id}
          (with-safe-log
            (tg/send-message telegram
                             chat-id
                             captcha-message
                             {:entities entities
                              :reply-markup {:inline_keyboard buttons}}))]

      (state/set-attrs state chat-id member-id
                       {:captcha-text captcha-text
                        :captcha-solution captcha-solution
                        :captcha-message-id captcha-message-id})

      (log/infof "Captcha sent, chat-id: %s, user-id: %s, username: %s, text: %s, solution: %s, message-id: %s"
                 chat-id member-id member-username captcha-text captcha-solution captcha-message-id)

      (with-safe-log
        (tg/restrict-user telegram chat-id member-id tg/chat-permissions-off))

      (log/infof "User restricted, chat-id: %s, user-id: %s, username: %s"
                 chat-id member-id member-username))))


(defn process-new-members
  [context message]
  (let [{:keys [me]}
        context

        {me-id :id}
        @me

        {:keys [new_chat_members]}
        message]

    (doseq [member new_chat_members

            :let
            [{:keys [id is_bot]}
             member]]

      ;; 1. do not block other bots (they're up to admins)
      ;; 2. do not block ourselves
      (when-not (or is_bot (= me-id id))
        (process-new-member context message member)))))


(defn command? [message]
  (some-> message :text (str/starts-with? "/")))


(defn health-command? [message]
  (some-> message :text (str/starts-with? "/health")))


(defn process-commands
  [context message]

  (let [{:keys [telegram]}
        context

        {:keys [chat
                message_id]}
        message

        {chat-id :id}
        chat]

    (cond

      (health-command? message)
      (with-safe-log
        (tg/send-message telegram
                         chat-id
                         "OK"
                         {:reply-to-message-id message_id})))))


(defn process-text
  [context message]

  (let [{:keys [state]}
        context

        {:keys [chat
                from
                message_id]}
        message

        {chat-id :id}
        chat

        {user-id :id}
        from

        {:keys [locked?]}
        (state/get-attrs state chat-id user-id)]

    (cond

      ;; Delete any message from a locked user. Sometimes,
      ;; spammers sneak between the "join" and "mute" events.
      locked?
      (delete-message context chat-id message_id)

      (command? message)
      (process-commands context message))))


(defn process-message
  [context message]

  (let [{:keys [config]}
        context

        {{:keys [message-expires]} :polling}
        config

        {:keys [text
                date
                new_chat_members]}
        message]

    (cond

      new_chat_members
      (when (almost-now date message-expires)
        (process-new-members context message))

      text
      (process-text context message))))


(defn process-callback-query
  [context callback-query]

  (let [{:keys [state
                config
                telegram]}
        context

        {{:keys [user-trail-attempts]} :polling}
        config

        {callback-id :id
         :keys [from
                data
                message]}
        callback-query

        {:keys [chat]}
        message

        {chat-id :id}
        chat

        {user-id :id}
        from

        user-name
        (get-full-name from)

        {:keys [locked?
                captcha-solution
                captcha-message-id
                joined-message-id]}
        (state/get-attrs state chat-id user-id)]

    (if-not locked?

      (with-safe-log
        (tg/answer-callback-query telegram callback-id))

      (if (= captcha-solution data)

        ;; solved
        (do
          (log/infof "Captcha solved, chat-id: %s, user-id: %s, username: %s, solution: %s"
                     chat-id user-id user-name data)
          (delete-message context chat-id captcha-message-id)
          (with-safe-log
            (tg/restrict-user telegram chat-id user-id tg/chat-permissions-on))
          (state/del-attrs state chat-id user-id)
          (with-safe-log
            (tg/answer-callback-query telegram
                                      callback-id
                                      {:text "Спасибо, ограничения сняты."
                                       :show-alert? true})))

        ;; otherwise
        (do
          (log/infof "Failed captcha attempt, chat-id: %s, user-id: %s, username: %s, solution: %s"
                     chat-id user-id user-name data)
          (state/inc-attr state chat-id user-id :attempt)
          (let [attempt
                (state/get-attr state chat-id user-id :attempt)]

            ;; no more attempts
            (if (> attempt user-trail-attempts)

              (do
                (delete-message context chat-id captcha-message-id)
                (terminate-user context chat-id user-id user-name)
                (state/del-attrs state chat-id user-id)
                (delete-message context chat-id joined-message-id)
                (with-safe-log
                  (tg/answer-callback-query telegram
                                            callback-id
                                            {:text "Попытки кончились."
                                             :show-alert? true})))

              (with-safe-log
                (tg/answer-callback-query telegram
                                          callback-id
                                          {:text "Ответ неверный."
                                           :show-alert? true})))))))))


(defn process-update
  [context update-entry]

  (let [{:keys [message
                callback_query]}
        update-entry]

    (cond

      ;; A regular message from a user.
      message
      (process-message context message)

      ;; Triggered when someone pushes a button in a captcha form.
      callback_query
      (process-callback-query context callback_query))))


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
    :keys [state
           config]}]

  (let [{{:keys [user-trail-period]} :polling}
        config

        unix-expired
        (- (unix-now) user-trail-period)

        triples
        (state/filter-by-attr state :date-joined :< unix-expired)]

    (doseq [[chat-id user-id attrs]
            triples]

      (let [{:keys [username
                    joined-message-id
                    captcha-message-id]}
            attrs]

        (delete-message context chat-id captcha-message-id)
        (terminate-user context chat-id user-id username)
        (delete-message context chat-id joined-message-id)
        (state/del-attrs state chat-id user-id)))))
