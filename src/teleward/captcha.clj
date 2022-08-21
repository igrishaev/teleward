(ns teleward.captcha)


(defn op->unicode [op]
  (case op
    + "\u002B"
    - "\u2212"
    * "\u00D7"))


(defn make-captcha
  "
  Retur a pair of captcha text and its solution, both strings.
  If `captcha-style` is `:lisp`, build something like `(+ 2 1)`
  rather than `2 + 1`. The operands are math unicode symbols
  so the text cannot be evaluated without Unicode denormalization.
  "
  [& [captcha-style]]
  (let [var1
        (+ 5 (rand-int 5))

        var2
        (+ 1 (rand-int 5))

        op
        (rand-nth '[+ - *])

        op-unicode
        (op->unicode op)

        form
        (case captcha-style
          (:lisp "lisp")
          (format "(%s %s %s)" op-unicode var1 var2)
          ;; else
          (format "%s %s %s" var1 op-unicode var2))

        solution
        (case op
          + (+ var1 var2)
          - (- var1 var2)
          * (* var1 var2))]

    [form (str solution)]))
