(ns teleward.ydb
  "
  https://cloud.yandex.ru/docs/ydb/docapi/api-ref/actions/updateItem
  https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.UpdateExpressions.html

  "
  (:require
   [clojure.string :as str]
   [clj-aws-sign.core :as aws-sign]
   [cheshire.core :as json]
   [org.httpkit.client :as http])
  (:import
   java.net.URI
   java.time.format.DateTimeFormatter
   java.time.ZoneId
   java.time.Instant))


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


(defn stream? [x]
  (instance? java.io.InputStream x))


(defn parse-response [{:keys [body]}]
  (cond
    (string? body)
    (json/parse-string body keyword)
    (stream? body)
    (json/parse-stream body keyword)
    :else
    (throw (new Exception "aaaaa"))))


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
        (clj-aws-sign.core/authorize
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

    @(http/request
      {:method :post
       :url endpoint
       :body payload
       :headers headers}
      parse-response)))


(defn ->aws [x]
  (cond

    (string? x)
    {:S x}

    (number? x)
    {:N (str x)}

    (boolean? x)
    {:BOOL x}

    :else
    (throw (new Exception "aaa"))))


(defn aws-> [[k v]]
  (case k
    :S    v
    :N    (parse-long v)
    ;; :M    parse-map
    ;; :L    parse-list
    :BOOL v))


(defn aws-deserialize [mapping]
  (reduce-kv
   (fn [result k v]
     (assoc result k (-> v first aws->)))
   {}
   mapping))


(defn attr->aws [x]
  (cond
    (keyword? x)
    (-> x str (subs 1))

    (string? x)
    x

    (symbol? x)
    (str x)

    :else
    (throw (new Exception "xxx"))))


(defn aws-serialize [mapping]
  (reduce-kv
   (fn [result k v]
     (assoc result (attr->aws k) (->aws v)))
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
                            (attr->aws k))
                  (assoc-in [:ExpressionAttributeValues (str ":" attr-sym)]
                            (->aws v)))))
          scope
          mapping)]

     (-> scope-new
         (update :UpdateExpression
                 str
                 " SET "
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
                            (attr->aws attr)))))
          scope
          attrs)]

     (-> scope*
         (update :UpdateExpression str
                 " REMOVE "
                 (str/join ", " (get scope* tmp)))
         (dissoc tmp)))))


(defn get-item [client table map-key]
  (let [response
        (make-request client "GetItem"
                      {:TableName table
                       :Key (aws-serialize map-key)})

        {:keys [Item]}
        response]

    (when Item
      (aws-deserialize Item))))


(defn put-item [client table mapping]
  (let [response
        (make-request client "PutItem"
                      {:TableName table
                       :Item (aws-serialize mapping)})]

    response))


#_
{:add {:foo 5}
 :set {:aaa  3 :dsf 3}
 :remove [:sss :ggg :sss]
 :delete {:colors "red"}}


(defn update-item [client table pk {:keys [add
                                           set
                                           remove
                                           delete
                                           return
                                           cond-expression]}]
  (let [params
        (cond-> {:TableName table
                 :Key (aws-serialize pk)}

          return
          (assoc :ReturnValues (case return
                                 :none "NONE"
                                 :all-old "ALL_OLD"
                                 :updated-old "UPDATED_OLD"
                                 :all-new "ALL_NEW"
                                 :updated-new "UPDATED_NEW"))

          cond-expression
          (assoc :ConditionExpression cond-expression)

          ;; add

          set
          (make-set-params set)

          remove
          (make-remove-params remove)

          ;; delete
          )

        response
        (make-request client "UpdateItem" params)]

    (some-> response :Attributes aws-deserialize)))


(defn delete-item [client table pk]
  (let [params
        {:TableName table
         :Key (aws-serialize pk)}

        response
        (make-request client "DeleteItem" params)]

    response))


(comment

  (def -c (make-client ""
                       ""
                       ""
                       "ru-central1"))

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
