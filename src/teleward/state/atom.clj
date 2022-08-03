(ns teleward.state.atom
  "
  An atom-driven state that tracks a map like
  chat-id => user => {attrs}
  inside.
  "
  (:require
   [teleward.state.api :as api]))


(defn in? [val collection]
  (contains? (set collection) val))


(defrecord AtomState [-state]

  api/IState

  (set-attr [_ chat-id user-id attr val]
    (swap! -state assoc-in [chat-id user-id attr] val))

  (set-attrs [_ chat-id user-id mapping]
    (swap! -state update-in [chat-id user-id] merge mapping))

  (get-attr [_ chat-id user-id attr]
    (get-in @-state [chat-id user-id attr]))

  (get-attrs [_ chat-id user-id]
    (get-in @-state [chat-id user-id]))

  (del-attr [_ chat-id user-id attr]
    (swap! -state update-in [chat-id user-id] dissoc attr))

  (del-attrs [_ chat-id user-id]
    (swap! -state update chat-id dissoc user-id))

  (inc-attr [_ chat-id user-id attr]
    (swap! -state update-in [chat-id user-id attr] (fnil inc 0)))

  (filter-by-attr [_ attr op value]

    (let [fn-op
          (case op
            (> :>) >
            (< :<) <
            (= :=) =
            (<= :<=) <=
            (>= :>=) >=
            (in :in) in?
            ;; else
            (throw (new Exception (format "Wrong op: %s" op))))]

      (for [[chat-id user-id->attrs] @-state
            [user-id attrs] user-id->attrs

            :when
            (some-> attrs (get attr) (fn-op value))]

        [chat-id user-id attrs]))))


(defn make-state [& _]
  (map->AtomState {:-state (atom {})}))


#_
(

 (def -s (make-state))

 (api/set-attr -s 1 2 :foo 1)
 (api/set-attr -s 1 3 :foo 9)

 (api/get-attrs -s 1 2)
 (api/get-attr -s 1 2 :foo)

 (api/inc-attr -s 1 2 :foo)

 (api/filter-by-attr -s :foo '> 3)

 (api/filter-by-attr -s :foo :in #{9})


 )
