(ns teleward.telegram
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [org.httpkit.client :as http]))


(defn filter-params [params]
  (persistent!
   (reduce-kv
    (fn [result k v]
      (if (some? v)
        (assoc! result k v)
        result))
    (transient {})
    params)))


(defn encode-params [params]
  (persistent!
   (reduce-kv
    (fn [result k v]
      (if (coll? v)
        (assoc! result k (json/generate-string v))
        (assoc! result k v)))
    (transient {})
    params)))


(defn api-request
  [{:keys [token
           user-agent
           timeout
           keepalive]}

   api-method http-method params]

  (let [params
        (filter-params params)

        url
        (format "https://api.telegram.org/bot%s/%s"
                token (name api-method))

        request
        {:url url
         :method http-method
         :as :stream}

        request
        (cond-> request

          user-agent
          (assoc :user-agent user-agent)

          timeout
          (assoc :timeout timeout)

          keepalive
          (assoc :keepalive keepalive))

        request
        (cond-> request

          (= :get http-method)
          (assoc :query-params (encode-params params))

          (= :post http-method)
          (->
           (assoc-in [:headers "content-type"] "application/json")
           (assoc :body (json/generate-string params))))

        {:keys [error status body headers]}
        @(http/request request)]

    (if error
      (throw (ex-info (format "Telegram HTTP error: %s %s %s")
                      {:api-method api-method
                       :api-params params}
                      error))

      (let [{:keys [content-type]}
            headers

            json?
            (some-> content-type
                    (str/starts-with? "application/json"))

            body-json
            (if json?
              (-> body io/reader (json/decode-stream keyword))
              (throw (ex-info (format "Telegram response was not JSON: %s" content-type)
                              {:http-status status
                               :http-method http-method
                               :http-headers headers
                               :api-method api-method
                               :api-params params})))

            {:keys [ok
                    result
                    error_code
                    description]}
            body-json]

        (if ok
          result
          (throw (ex-info (format "Telegram API error: %s %s %s"
                                  error_code api-method description)
                          {:http-status status
                           :http-method http-method
                           :api-method api-method
                           :api-params params
                           :error-code error_code
                           :error description})))))))


(defn get-updates
  "https://core.telegram.org/bots/api#getupdates"

  ([config]
   (get-updates config nil))

  ([config {:keys [limit
                   offset
                   timeout
                   allowed-updates]}]

   (api-request config
                :getUpdates
                :get
                {:limit limit
                 :offset offset
                 :timeout timeout
                 :allowed_updates allowed-updates})))


(defn ban-user
  "https://core.telegram.org/bots/api#banchatmember"

  ([config chat-id user-id]
   (ban-user config chat-id user-id nil))

  ([config chat-id user-id {:keys [until-date
                                   revoke-messages]}]

   (api-request config
                :banChatMember
                :post
                {:chat_id chat-id
                 :user_id user-id
                 :until_date until-date
                 :revoke_messages revoke-messages})))

(defn send-message
  "https://core.telegram.org/bots/api#sendmessage"

  ([config chat-id text]
   (send-message config chat-id text nil))

  ([config chat-id text {:keys [parse-mode
                                entities
                                disable-web-page-preview
                                disable-notification
                                protect-content
                                reply-to-message-id
                                allow-sending-without-reply
                                reply-markup]}]

   (api-request config
                :sendMessage
                :post
                {:chat_id chat-id
                 :text text
                 :parse_mode parse-mode
                 :entities entities
                 :disable_web_page_preview disable-web-page-preview
                 :disable_notification disable-notification
                 :protect_content protect-content
                 :reply_to_message_id reply-to-message-id
                 :allow_sending_without_reply allow-sending-without-reply
                 :reply_markup reply-markup})))


(defn delete-message
  "https://core.telegram.org/bots/api#deletemessage"
  [config chat-id message-id]
  (api-request config
                :deleteMessage
                :post
                {:chat_id chat-id
                 :message_id message-id}))


(def chat-permission-types
  #{:can_send_messages
    :can_send_media_messages
    :can_send_polls
    :can_send_other_messages
    :can_add_web_page_previews
    :can_change_info
    :can_invite_users
    :can_pin_messages})

(def chat-permissions-on
  (zipmap chat-permission-types (repeat true)))

(def chat-permissions-off
  (zipmap chat-permission-types (repeat false)))


(defn restrict-user

  ([config chat-id user-id permissions]
   (restrict-user config chat-id user-id permissions nil))

  ([config chat-id user-id permissions {:keys [until_date]}]
   (api-request config
                :restrictChatMember
                :post
                {:chat_id chat-id
                 :user_id user-id
                 :permissions permissions
                 :until_date until_date})))


#_
(

 (def telegram
   {:token "..."
    :user-agent "Clojure 1.10.3"
    :timeout 300000
    :keepalive 300000})

 (get-updates telegram {:timeout 30 :offset 75640811})

 (ban-user telegram -721166690 223429441 {:unix-until 0})

 (send-message telegram -721166690 "hello!")

 (send-message telegram -721166690 "hello!"
               {:reply-markup
                {:inline_keyboard
                 [[{:text "a"}
                   {:text "b"}
                   {:text "c"}]
                  [{:text "d"}
                   {:text "e"}
                   {:text "f"}]]}})

 (restrict-user telegram -721166690 223429441 {:can_send_messages false})

 (restrict-user telegram -1001175355067 873472876 {:can_send_messages false})


 )
