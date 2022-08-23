(ns teleward.yc-function.state
  "
  DynamoDB/YandexDB-driven state.
  "
  (:require
   [amazonica.core]

   [clj-aws-sign.core :as aws-sign]


   ;; [amazonica.aws.dynamodbv2 :as dynamodb]
   [clojure.string :as str]
   #_
   [taoensso.faraday :as far]
   [teleward.state.api :as api]))


(defn pk [chat-id user-id]
  {:chat_id chat-id :user_id user-id})


(defn ->attr [kw]
  (name kw))


(defn make-set-params [key->vals]

  (-> (reduce-kv
       (fn [result attr-key attr-val]
         (let [attr-sym (gensym "attr")]
           (-> result
               (update :update-expr (fnil conj []) (format "#%s = :%s" attr-sym attr-sym))
               (assoc-in [:expr-attr-names (format "#%s" attr-sym)] (->attr attr-key))
               (assoc-in [:expr-attr-vals (format ":%s" attr-sym)] attr-val))))
       {}
       key->vals)

      (update :update-expr
              (fn [pairs]
                (format "SET %s" (str/join ", " pairs))))))


(defn cleanup-pk [row]
  (dissoc row :chat_id :user_id))


#_
(defrecord DynamoDBState
    [access-key
     secret-key
     endpoint
     table]

    api/IState

    (set-attr [this chat-id user-id attr val]
      (far/update-item this
                       table
                       (pk chat-id user-id)
                       (make-set-params {attr val})))

    (set-attrs [this chat-id user-id mapping]
      (far/update-item this
                       table
                       (pk chat-id user-id)
                       (make-set-params mapping)))

    (get-attr [this chat-id user-id attr]
      (-> (far/get-item this
                        table
                        (pk chat-id user-id)
                        {:attrs [(->attr attr)]})
          (get attr)))

    (get-attrs [this chat-id user-id]
      (-> (far/get-item this table (pk chat-id user-id))
          (cleanup-pk)))

    (del-attr [this chat-id user-id attr]
      (far/update-item this
                       table
                       (pk chat-id user-id)
                       {:update-expr "REMOVE #attr"
                        :expr-attr-names {"#attr" (->attr attr)}}))

    (del-attrs [this chat-id user-id]
      (far/delete-item this table (pk chat-id user-id)))

    (inc-attr [this chat-id user-id attr]
      (far/update-item this
                       table
                       (pk chat-id user-id)
                       {:update-expr "SET #attr = #attr + :delta"
                        :expr-attr-vals {":delta" 1}
                        :expr-attr-names {"#attr" (->attr attr)}}))

    (filter-by-attr [this attr op value]
      (let [scan-params
            {:attr-conds {(->attr attr) [op value]}}

            result
            (far/scan this table scan-params)]

        (into []
              (for [{:as row :keys [chat_id user_id]}
                    result]
                [chat_id user_id (cleanup-pk row)])))))


(defn get-env [env-name]
  (or (System/getenv env-name)
      (throw (new Exception (format "The '%s' environment variable not set!" env-name)))))


#_
(defn make-state [& [_options]]
  (map->DynamoDBState
   {:access-key (get-env "AWS_ACCESS_KEY_ID")
    :secret-key (get-env "AWS_SECRET_ACCESS_KEY")
    :endpoint (get-env "DYNAMODB_ENDPOINT")
    :table (get-env "DYNAMODB_TABLE")}))


#_
(comment

  (def -s (make-state))

  (api/get-attrs -s 2 1)

  (api/get-attr -s 2 1 :user-name)

  (api/set-attr -s 2 1 :user-name "John")

  (api/get-attrs -s 2 1)

  (api/del-attr -s 2 1 :user-name)

  (api/del-attrs -s 2 1)

  (api/set-attrs -s 2 1 {:counter 0})

  (api/inc-attr -s 2 1 :counter)

  (api/filter-by-attr -s :counter :> 0)

  )


(let [method "POST"
      service "dynamodb"
      host "docapi.serverless.yandexcloud.net"
      region "ru-central1"
      endpoint "https://docapi.serverless.yandexcloud.net/"
      content-type "application/x-amz-json-1.0"
      amz_target "DynamoDB_20120810.GetItem"

      payload
      (cheshire.core/generate-string
       {:TableName "table258"
        :Key {"chat_id" {"N" "1"}
              "user_id" {"N" "1"}}})

      content-length
      (count payload)

      uri "/ru-central1/..."

      date
      "20220823T150056Z"

      auth-header
      (clj-aws-sign.core/authorize
       {:method method
        :uri uri
        :date date
        :headers {"host" host
                  "content-type" content-type
                  "x-amz-date" date
                  "x-amz-target" amz_target}
        :payload payload
        :service service
        :region region
        :access-key ""
        :secret-key ""})

      headers
      {"authorization" auth-header
       "content-type" content-type
       "x-amz-target" amz_target
       "x-amz-date" date}

      response
      (org.httpkit.client/request
       {:method :post
        :url "https://docapi.serverless.yandexcloud.net/..."
        :body payload
        :headers headers})]

  (:body @response))
