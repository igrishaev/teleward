(ns teleward.captcha)


(defn gen-rands [limit cols rows]
  (into {}
        (for [r (range rows)
              c (range cols)]
          [[r c] (rand-int limit)])))


(defn add-solution [rands solution cols rows]
  (let [r (rand-int rows)
        c (if (== 0 r)
            (+ 1 (rand-int (- 1 cols)))
            (rand-int cols))]
    (assoc rands [r c] solution)))


(defn gen-buttons [solution limit cols rows]
  (let [rands
        (-> limit
            (gen-rands cols rows)
            (add-solution solution cols rows))]
    (for [r (range rows)]
      (for [c (range cols)]
        (let [text
              (str (get rands [r c]))]
          {:text text :callback_data text})))))


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
