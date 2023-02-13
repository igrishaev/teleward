(ns teleward.state-ydb
  "
  DynamoDB/YandexDB-driven state.
  "
  (:require
   [dynamodb.api :as db]
   [teleward.state :as state]))


(defn pk [chat-id user-id]
  {:chat_id chat-id :user_id user-id})


(defrecord YDBState
    [client
     table]

  state/IState

  (set-attr [_ chat-id user-id attr val]
    (-> (db/update-item client
                        table
                        (pk chat-id user-id)
                        {:set {attr val}})
        :Attributes))

  (set-attrs [_ chat-id user-id mapping]
    (-> (db/update-item client
                        table
                        (pk chat-id user-id)
                        {:set mapping})
        :Attributes))

  (get-attr [_ chat-id user-id attr]
    (-> (db/get-item client
                     table
                     (pk chat-id user-id)
                     {:attrs-get [attr]})
        (get-in [:Item attr])))

  (get-attrs [_ chat-id user-id]
    (-> (db/get-item client
                     table
                     (pk chat-id user-id))
        :Item))

  (del-attr [_ chat-id user-id attr]
    (-> (db/update-item client
                        table
                        (pk chat-id user-id)
                        {:remove [attr]})
        :Attributes))

  (del-attrs [_ chat-id user-id]
    (db/delete-item client
                    table
                    (pk chat-id user-id)))

  (inc-attr [_ chat-id user-id attr]
    (-> (db/update-item client
                     table
                     (pk chat-id user-id)
                     {:add {attr 1}})
        :Attributes))

  (filter-by-attr [_ attr op value]
    (let [{:keys [Items]}
          (db/scan client
                   table
                   {:sql-filter (format "#attr %s :value" (name op))
                    :attr-names {"#attr" attr}
                    :attr-values {":value" value}})]
      (for [{:as item
             :keys [chat_id
                    user_id]} Items]
        [chat_id user_id item]))))


(defn get-env [env-name]
  (or (System/getenv env-name)
      (throw (new Exception (format "The '%s' environment variable not set!" env-name)))))


(defn make-state []

  (let [client
        (db/make-client
         (get-env "AWS_ACCESS_KEY_ID")
         (get-env "AWS_SECRET_ACCESS_KEY")
         (get-env "DYNAMODB_ENDPOINT")
         (get-env "AWS_REGION")
         {:throw? true})

        table
        (get-env "DYNAMODB_TABLE") ]

    (map->YDBState {:client client :table table})))


#_
(comment

  (def -s (make-state))

  (state/get-attrs -s 3 5)

  (state/get-attr -s 3 5 :kek)

  (state/set-attr -s 2 1 :user-name "John")

  (state/get-attrs -s 2 1)

  (state/del-attr -s 2 1 :user-name)

  (state/del-attrs -s 2 1)

  (state/set-attrs -s 2 1 {:counter 0})

  (state/inc-attr -s 3 5 :test/bbb)
  (state/get-attrs -s 3 5)

  (state/filter-by-attr -s :date-joined '> 0)

  (state/filter-by-attr -s :test/aaa '= 123)

  )
