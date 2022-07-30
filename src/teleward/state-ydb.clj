(ns teleward.ydb
  (:require [taoensso.faraday :as far]))

(def ^{:private true} table (System/getenv "DYNAMODB_TABLE"))

(defn ^{:private true} get-update-exp [exp attrs]
  (->> attrs
       (map #(str "#" % " = :" %))
       (clojure.string/join ",")
       (str exp " ")))

(defn set-attr [state chat-id user-id attr val]
  (far/update-item state table
    {:chat_id chat-id :user_id user-id}
    {:update-expr "SET #attr = :val"
     :expr-attr-names {"#attr" (name attr)}
     :expr-attr-vals {":val" val}}))

(defn get-attr [state chat-id user-id attr]
  (attr
    (far/get-item state table {:chat_id chat-id :user_id user-id})))

(defn del-attr [state chat-id user-id attr]
  (far/update-item state table
    {:chat_id chat-id :user_id user-id}
    {:update-expr "REMOVE #attr"
     :expr-attr-names {"#attr" (name attr)}}))

(defn inc-attr [state chat-id user-id attr]
  (swap! state update-in [chat-id user-id attr] (fnil inc 0)))

(defn set-attrs [state chat-id user-id mapping]
  (let [attrs (for [[attr val] mapping] (name attr))
        update-exp     (get-update-exp "SET" attrs)
        exp-attr-names (->> attrs
                            (map #(vec [(str "#" %) %]))
                            (into {}))
        exp-attr-vals  (->> attrs
                            (map #(vec [(str ":" %) (mapping (keyword %))]))
                            (into {}))]
    (far/update-item state table
      {:chat_id chat-id :user_id user-id}
      {:update-expr update-exp
       :expr-attr-names exp-attr-names
       :expr-attr-vals exp-attr-vals})))

(defn get-attrs [state chat-id user-id]
  (far/get-item state table {:chat_id chat-id :user_id user-id}))

(defn del-attrs [state chat-id user-id]
  (far/put-item state table {:chat_id chat-id :user_id user-id}))

(defn iter-attrs [state]
  (map #(vec [(:chat_id %) (:user_id %) %]) (far/scan state table)))

(defn make-state []
  {:access-key (System/getenv "AWS_ACCESS_KEY_ID")
   :secret-key (System/getenv "AWS_SECRET_ACCESS_KEY")
   :endpoint (System/getenv "DYNAMODB_ENDPOINT")})