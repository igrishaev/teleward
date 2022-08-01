(ns teleward.state.api)


(defprotocol IState

  (set-attr [state chat-id user-id attr val]
    "Set a single attribute.")

  (set-attrs [state chat-id user-id mapping]
    "Set multiple attributes at once.")

  (get-attr [state chat-id user-id attr]
    "Set multiple attributes at once.")

  (get-attrs [state chat-id user-id]
    "Get all the attributes at once.")

  (del-attr [state chat-id user-id attr]
    "Delete a single attrubute.")

  (del-attrs [state chat-id user-id]
    "Delete all the attrubutes.")

  (inc-attr [state chat-id user-id attr]
    "Increase an integer attribute.")

  (filter-by-attr [state attr op value]
    "Filter rows by an attribute, a binary operator
     (e.g <, >, =, etc) and a value.
     Must return triples [chat-id, user-id, attrs]"))
