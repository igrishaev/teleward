(ns teleward.locale
  "
  Locale-dependant utilities.
  "
  (:require
   [clojure.string :as str]))


(def captcha-templates
  {:en
   "
Hello @%s! In order to participate, please provide an answer to the following arithmetic expression:

%s

Until then, your messages will be deleted, and you will be kicked soon. Thank you!
"
   :ru
   "
Привет, @%s! Чтобы присоединиться, напишите решение задачи:

%s

До тех пор сообщения будут удаляться, и скоро вас исключат. Спасибо!
"
   })


(def captcha-templates
  (update-vals captcha-templates str/trim))


(defn get-captcha-message
  [lang username captcha]
  (let [template
        (or (get captcha-templates lang)
            (get captcha-templates :en))]
    (format template username captcha)))
