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

  (set-attr [_ chat-id user-id attr val]
    (:Attributes
     (ydb/update-item client
                      table
                      (pk chat-id user-id)
                      {:set {attr val}})))

  (set-attrs [_ chat-id user-id mapping]
    (:Attributes
     (ydb/update-item client
                      table
                      (pk chat-id user-id)
                      {:set mapping})))

  (get-attr [_ chat-id user-id attr]
    (get-in
     (ydb/get-item client
                   table
                   (pk chat-id user-id)
                   {:attrs [attr]})
     [:Item attr]))

  (get-attrs [_ chat-id user-id]
    (:Item (ydb/get-item client table (pk chat-id user-id))))

  (del-attr [_ chat-id user-id attr]
    (:Attributes
     (ydb/update-item client
                      table
                      (pk chat-id user-id)
                      {:remove [attr]})))

  (del-attrs [_ chat-id user-id]
    (ydb/delete-item client table (pk chat-id user-id)))

  (inc-attr [_ chat-id user-id attr]
    (:Attributes
     (ydb/update-item client
                      table
                      (pk chat-id user-id)
                      {:add {attr 1}})))

  (filter-by-attr [_ attr op value]
    (:Items
     (ydb/scan client
               table
               {:filter-expr (format "#attr %s :value" (name op))
                :attr-names {:attr attr}
                :attr-values {:value value}}))))


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

  (state/get-attrs -s 3 5)

  (state/get-attr -s 3 5 :kek)

  (state/set-attr -s 2 1 :user-name "John")

  (state/get-attrs -s 2 1)

  (state/del-attr -s 2 1 :user-name)

  (state/del-attrs -s 2 1)

  (state/set-attrs -s 2 1 {:counter 0})

  (state/inc-attr -s 3 5 :test/bbb)
  (state/get-attrs -s 3 5)

  (state/filter-by-attr -s :test/aaa '= 123)

  )
