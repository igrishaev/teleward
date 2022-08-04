(ns teleward.yc-function.state
  "
  DynamoDB/YandexDB-driven state.
  "
  (:require
   [clojure.string :as str]
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


(defn make-state [& [options]]
  (map->DynamoDBState
   {:access-key (System/getenv "AWS_ACCESS_KEY_ID")
    :secret-key (System/getenv "AWS_SECRET_ACCESS_KEY")
    :endpoint (System/getenv "DYNAMODB_ENDPOINT")
    :table (System/getenv "DYNAMODB_TABLE")}))


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
