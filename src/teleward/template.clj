(ns teleward.template)

(def captcha-ru
  ["Привет, " :user "! Чтобы присоединиться, решите задачу: "
   \newline
   :captcha
   \newline
   "До тех пор вы не сможете писать сообщения, и скоро вас удалят."])


(def captcha-en
  ["Hello " :user "! In order to participate, please solve an expression: "
   :captcha
   \newline
   "Until then, you cannot send messages, and you will be kicked soon."])


(defn user-mention [user]
  (let [{:keys [first_name
                last_name]}
        user

        full-name
        (str first_name
             (when last_name
               (str " " last_name)))]

    [:text_mention full-name {:user user}]))


(defn get-captcha-template [locale]
  (case locale
    (:ru "ru") captcha-ru
    (:en "en") captcha-en
    ;; else
    captcha-en))


(defn render [template & [context]]
  (reduce
   (fn [{:as result :keys [message]} item]
     (cond

       (or (string? item) (char? item))
       (-> result
           (update :message str item))

       (keyword? item)
       (let [item
             (or (get context item)
                 (throw (new Exception
                             (format "missing context: %s" item))))]
         (recur result item))

       (vector? item)
       (let [[tag item extra]
             item

             entity
             (merge {:type tag
                     :offset (count message)
                     :length (count item)}
                    extra)]
         (-> result
             (update :message str item)
             (update :entities conj entity)))))

   {:message "" :entities []}

   template))
