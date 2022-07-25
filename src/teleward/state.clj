(ns teleward.state)

;;
;; A bunch of functions to track the state of the world.
;; The content of the state atom is a map like this:
;; chat-id => user => {attrs}
;; The functions provide CRUD operations for a single attribute
;; and multiple attributes as well (with -s at the end).
;;


(defn set-attr [state chat-id user-id attr val]
  (swap! state assoc-in [chat-id user-id attr] val))

(defn get-attr [state chat-id user-id attr]
  (get-in @state [chat-id user-id attr]))

(defn del-attr [state chat-id user-id attr]
  (swap! state update-in [chat-id user-id] dissoc attr))

(defn inc-attr [state chat-id user-id attr]
  (swap! state update-in [chat-id user-id attr] (fnil inc 0)))

(defn set-attrs [state chat-id user-id mapping]
  (swap! state update-in [chat-id user-id] merge mapping))

(defn get-attrs [state chat-id user-id]
  (get-in @state [chat-id user-id]))

(defn del-attrs [state chat-id user-id]
  (swap! state update chat-id dissoc user-id))

(defn iter-attrs [state]
  (for [[chat-id user-id->attrs] @state
        [user-id attrs] user-id->attrs]
    [chat-id user-id attrs]))


(defn make-state []
  (atom {}))
