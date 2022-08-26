(ns teleward.ydb
  "
  https://cloud.yandex.ru/docs/ydb/docapi/api-ref/actions/updateItem
  https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.UpdateExpressions.html
  "
  (:require
   [cheshire.core :as json]
   [clj-aws-sign.core :as aws-sign]
   [clojure.string :as str]
   [org.httpkit.client :as http])
  (:import
   java.net.URI
   java.time.Instant
   java.time.ZoneId
   java.time.format.DateTimeFormatter))


(def ^DateTimeFormatter
  formatter
  (-> "yyyyMMdd'T'HHmmss'Z'"
      (DateTimeFormatter/ofPattern)
      (.withZone (ZoneId/of "UTC"))))


(defn make-client
  [access-key
   secret-key
   endpoint
   region]

  (let [uri
        (new URI endpoint)

        host
        (.getHost uri)

        path
        (.getPath uri)]

    {:access-key access-key
     :secret-key secret-key
     :endpoint endpoint
     :content-type "application/x-amz-json-1.0"
     :host host
     :path path
     :service "dynamodb"
     :version "20120810"
     :region region}))


(defn error! [template & args]
  (throw (new Exception ^String (apply format template args))))


(defn stream? [x]
  (instance? java.io.InputStream x))


(defn parse-response [{:as response :keys [body]}]
  (cond

    (string? body)
    (update response :body json/parse-string keyword)

    (stream? body)
    (update response :body json/parse-stream keyword)

    :else
    (error! "Wrong response body type: %s" (type body))))


(defn maybe-throw-response [{:as response :keys [body]}]
  (let [{:keys [__type message]}
        body]
    (if (and __type message)
      (let [{:keys [status
                    opts]}
            response

            {:keys [method
                    headers
                    url]}
            opts]
        (throw (ex-info "DynamoDB error"
                        {:url url
                         :status status
                         :method method
                         :headers headers
                         :error-type __type
                         :error-message message})))
      body)))


(def response-handler
  (comp maybe-throw-response parse-response))


(defn make-request
  [{:keys [host path region access-key secret-key endpoint content-type version service]}
   target
   data]

  (let [payload
        (json/generate-string data)

        date
        (.format formatter (Instant/now))

        amz_target
        (format "DynamoDB_%s.%s" version target)

        auth-header
        (aws-sign/authorize
         {:method "POST"
          :uri path
          :date date
          :headers {"host" host
                    "content-type" content-type
                    "x-amz-date" date
                    "x-amz-target" amz_target}
          :payload payload
          :service service
          :region region
          :access-key access-key
          :secret-key secret-key})

        headers
        {"authorization" auth-header
         "content-type" content-type
         "x-amz-target" amz_target
         "x-amz-date" date}]

    (-> {:method :post
         :url endpoint
         :body payload
         :headers headers}

        (http/request)
        (deref)
        (parse-response)
        (maybe-throw-response))))


(defn value-encode [x]
  (cond

    (string? x)
    {:S x}

    (number? x)
    {:N (str x)}

    (boolean? x)
    {:BOOL x}

    :else
    (error! "Unsupported value to encode: %s" x)))


(defn value-decode [[k v]]
  (case k
    :S v
    :N (parse-long v)
    :BOOL v
    (error! "Unsupported value to decode: %s %s" k v)))


(defn attr-encode [x]
  (cond
    (keyword? x)
    (-> x str (subs 1))

    (string? x)
    x

    (symbol? x)
    (str x)

    :else
    (error! "Wrong attribute: %s" x)))


(defn item-encode [mapping]
  (reduce-kv
   (fn [result k v]
     (assoc result (attr-encode k) (value-encode v)))
   {}
   mapping))


(defn item-decode [mapping]
  (reduce-kv
   (fn [result k v]
     (assoc result k (-> v first value-decode)))
   {}
   mapping))


(defn make-set-params

  ([mapping]
   (make-set-params nil mapping))

  ([scope mapping]
   (let [tmp :__pairs

         scope-new
         (reduce-kv
          (fn [result k v]
            (let [attr-sym (gensym "attr")]
              (-> result
                  (update tmp (fnil conj [])
                          (format "#%s = :%s" attr-sym attr-sym))
                  (assoc-in [:ExpressionAttributeNames (str "#" attr-sym)]
                            (attr-encode k))
                  (assoc-in [:ExpressionAttributeValues (str ":" attr-sym)]
                            (value-encode v)))))
          scope
          mapping)]

     (-> scope-new
         (update :UpdateExpression
                 str
                 " SET "
                 (str/join ", " (get scope-new tmp)))
         (dissoc tmp)))))


(defn make-add-params
  ([mapping]
   (make-add-params nil mapping))

  ([scope mapping]
   (let [tmp :__pairs

         scope-new
         (reduce-kv
          (fn [result k v]
            (let [attr-sym (gensym "attr")]
              (-> result
                  (update tmp (fnil conj [])
                          (format "#%s :%s" attr-sym attr-sym))
                  (assoc-in [:ExpressionAttributeNames (str "#" attr-sym)]
                            (attr-encode k))
                  (assoc-in [:ExpressionAttributeValues (str ":" attr-sym)]
                            (value-encode v)))))
          scope
          mapping)]

     (-> scope-new
         (update :UpdateExpression
                 str
                 " ADD "
                 (str/join ", " (get scope-new tmp)))
         (dissoc tmp)))))


(defn make-remove-params
  ([attrs]
   (make-remove-params nil attrs))

  ([scope attrs]
   (let [tmp :__attrs

         scope*
         (reduce
          (fn [result attr]
            (let [attr-sym (gensym "attr")]
              (-> result
                  (update tmp (fnil conj []) (str "#" attr-sym))
                  (assoc-in [:ExpressionAttributeNames (str "#" attr-sym)]
                            (attr-encode attr)))))
          scope
          attrs)]

     (-> scope*
         (update :UpdateExpression str
                 " REMOVE "
                 (str/join ", " (get scope* tmp)))
         (dissoc tmp)))))


(defn set-return-vals [params kw]
  (assoc params :ReturnValues
         (case kw
           :none "NONE"
           :all-old "ALL_OLD"
           :updated-old "UPDATED_OLD"
           :all-new "ALL_NEW"
           :updated-new "UPDATED_NEW"
           (error! "Wrong ReturnValues: %s" kw))))


;;
;; API
;;

(defn get-item
  ([client table pk]
   (get-item client table pk nil))

  ([client table pk {:keys [attrs]}]
   (let [params
         (cond-> {:TableName table
                  :Key (item-encode pk)}

           attrs
           (assoc :AttributesToGet
                  (mapv attr-encode attrs)))

         response
         (make-request client "GetItem" params)]

     (update response :Item item-decode))))



(defn put-item
  ([client table item]
   (put-item client table item nil))

  ([client table item {:keys [return]}]
   (let [params
         (cond-> {:TableName table
                  :Item (item-encode item)}

           return
           (set-return-vals return))

         response
         (make-request client "PutItem" params)]

     (update response :Attributes item-decode))))


#_
{:add {:foo 5}
 :set {:aaa  3 :dsf 3}
 :remove [:sss :ggg :sss]
 :delete {:colors "red"}}


(defn update-item [client table pk {:keys [add
                                           set
                                           remove
                                           ;; delete
                                           return
                                           cond-expression]}]
  (let [params
        (cond-> {:TableName table
                 :Key (item-encode pk)}

          return
          (set-return-vals return)

          cond-expression
          (assoc :ConditionExpression cond-expression)

          add
          (make-add-params add)

          set
          (make-set-params set)

          remove
          (make-remove-params remove)

          ;; delete
          ;; (make-delete-params delete

          )

        response
        (make-request client "UpdateItem" params)]

    (update response :Attributes item-decode)))


(defn delete-item

  ([client table pk]
   (delete-item client table pk nil))

  ([client table pk {:keys [return]}]

   (let [params
         (cond-> {:TableName table
                  :Key (item-encode pk)}
           return
           (set-return-vals return))

         response
         (make-request client "DeleteItem" params)]

     (update response :Attributes item-decode))))


(defn encode-attr-names
  [attr-names]
  (reduce-kv
   (fn [result k v]
     (assoc result (str "#" (name k)) (attr-encode v)))
   {}
   attr-names))


(defn encode-attr-values
  [attr-values]
  (reduce-kv
   (fn [result k v]
     (assoc result (str ":" (name k)) (value-encode v)))
   {}
   attr-values))


(defn scan
  ([client table]
   (scan client table nil))

  ([client table {:keys [start-key
                         filter-expr
                         attr-names
                         attr-values
                         limit]}]

   (let [params
         (cond-> {:TableName table}

           start-key
           (assoc :ExclusiveStartKey start-key)

           filter-expr
           (assoc :FilterExpression filter-expr)

           limit
           (assoc :Limit limit)

           attr-names
           (assoc :ExpressionAttributeNames
                  (encode-attr-names attr-names))

           attr-values
           (assoc :ExpressionAttributeValues
                  (encode-attr-values attr-values)))

         response
         (make-request client "Scan" params)]

     (update response :Items
             (fn [items]
               (mapv item-decode items))))))



#_
(comment

  (def -r (get-item -c "table258" {:chat_id 1 :user_id 5}))

  (def -r (put-item -c "table258" {:chat_id 3
                                   :user_id 3
                                   :foo/test 42}))

  (def -r (update-item -c "table258"
                       {:chat_id 1
                        :user_id 3}
                       {:set {:some/test1 "AAA" :some/test2 123123}}))

  (update-item -c "table258"
               {:chat_id 1
                :user_id 3}
               {:remove [:aa :bb :bool]})

  )
