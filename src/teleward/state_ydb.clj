(ns teleward.state-ydb
  "
  DynamoDB/YandexDB-driven state.
  "
  (:require
   [teleward.ydb :as ydb]
   [teleward.state :as state]))


(defn pk [chat-id user-id]
  {:chat_id chat-id :user_id user-id})


(defrecord YDBState
    [client
     table]

  state/IState

  (set-attr [this chat-id user-id attr val]
    (:Attributes
     (ydb/update-item client
                      table
                      (pk chat-id user-id)
                      {:set {attr val}})))

  (set-attrs [this chat-id user-id mapping]
    (:Attributes
     (ydb/update-item client
                      table
                      (pk chat-id user-id)
                      {:set mapping})))

  #_
  (get-attr [this chat-id user-id attr]
    (-> (far/get-item this
                      table
                      (pk chat-id user-id)
                      {:attrs [(->attr attr)]})
        (get attr)))

  (get-attrs [this chat-id user-id]
    (:Item (ydb/get-item client table (pk chat-id user-id))))

  #_
  (del-attr [this chat-id user-id attr]
    (far/update-item this
                     table
                     (pk chat-id user-id)
                     {:update-expr "REMOVE #attr"
                      :expr-attr-names {"#attr" (->attr attr)}}))

  #_
  (del-attrs [this chat-id user-id]
    (far/delete-item this table (pk chat-id user-id)))

  #_
  (inc-attr [this chat-id user-id attr]
    (far/update-item this
                     table
                     (pk chat-id user-id)
                     {:update-expr "SET #attr = #attr + :delta"
                      :expr-attr-vals {":delta" 1}
                      :expr-attr-names {"#attr" (->attr attr)}}))

  #_
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


(defn make-state []

  (let [client
        (ydb/make-client
         (get-env "AWS_ACCESS_KEY_ID")
         (get-env "AWS_SECRET_ACCESS_KEY")
         (get-env "DYNAMODB_ENDPOINT")
         (get-env "AWS_REGION"))

        table
        (get-env "DYNAMODB_TABLE")]

    (map->YDBState {:client client :table table})))


#_
(comment

  (def -s (make-state))

  (state/get-attrs -s 2 1)

  (state/get-attr -s 2 1 :user-name)

  (state/set-attr -s 2 1 :user-name "John")

  (state/get-attrs -s 2 1)

  (state/del-attr -s 2 1 :user-name)

  (state/del-attrs -s 2 1)

  (state/set-attrs -s 2 1 {:counter 0})

  (state/inc-attr -s 2 1 :counter)

  (state/filter-by-attr -s :counter :> 0)

  )
