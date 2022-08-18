(ns teleward.template)

(def captcha-ru
  ["Привет, "
   :user
   "! Чтобы присоединиться, напишите решение задачи:"
   \newline
   \newline
   :captcha
   \newline
   \newline
   "До тех пор сообщения будут удаляться, и скоро вас исключат. Спасибо!"])


(def captcha-en

  ["Hello "
   :user
   "! In order to participate, please provide an answer to the following arithmetic expression:"
   \newline
   \newline
   :captcha
   \newline
   \newline
   "Until then, your messages will be deleted, and you will be kicked soon. Thank you!"])


(defn get-captcha-template [locale]
  (cond
    (= locale :ru) captcha-ru
    :else captcha-en))


(defn render [template & [context]]
  (reduce
   (fn [{:as result :keys [message entities]} item]
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
