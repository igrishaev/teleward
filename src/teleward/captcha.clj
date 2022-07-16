(ns teleward.captcha)


(defn op->unicode [op]
  (case op
    + "\u002B"
    - "\u2212"
    * "\u00D7"))


(defn make-captcha [& [captcha-style]]
  (let [var1
        (+ 5 (rand-int 5))

        var2
        (+ 1 (rand-int 5))

        op
        (rand-nth '[+ - *])

        op-unicode
        (op->unicode op)

        form
        (cond
          (= :lisp captcha-style)
          (format "(%s %s %s)" op-unicode var1 var2)

          :else
          (format "%s %s %s" var1 op-unicode var2))

        solution
        (case op
          + (+ var1 var2)
          - (- var1 var2)
          * (* var1 var2))]

    [form (str solution)]))
