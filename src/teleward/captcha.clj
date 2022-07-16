(ns teleward.captcha)

(defn make-captcha []
  (let [var1
        (+ 5 (rand-int 5))

        var2
        (+ 1 (rand-int 5))

        op
        (rand-nth '[+ - *])

        form
        (list op var1 var2)

        solution
        (case op
          + (+ var1 var2)
          - (- var1 var2)
          * (* var1 var2))]

    [(str form) (str solution)]))
