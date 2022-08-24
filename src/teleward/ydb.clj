(ns teleward.ydb
  (:require
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
    :N    (read-string v)
    :M    42
    :BOOL v))


(defn aws-deserialize [mapping]
  (reduce-kv
   (fn [result k v]
     (assoc result k (-> v first aws->)))
   {}
   mapping))


(defn aws-serialize [mapping]
  (reduce-kv
   (fn [result k v]
     (assoc result (name k) (->aws v)))
   {}
   mapping))


(defn get-item [client table map-key]
  (let [response
        (make-request client "GetItem"
                      {:TableName table
                       :Key (aws-serialize map-key)})

        {:keys [Item]}
        response]

    (when Item
      (aws-deserialize Item))))


(defn put-item [client table map-key map-attrs]
  (let [response
        (make-request client "PutItem"
                      {:TableName table
                       :Key (aws-serialize map-key)
                       ;; :ExpressionAttributeValues {:foo {:S "AAAAAA"}}
                       ;; :UpdateExpression "set aaa=:foo"

                       ;; :AttributeUpdates (aws-serialize map-attrs)

                       })
        ]

    response))

(defn update-item [client table map-key map-attrs]
  (let [response
        (make-request client "UpdateItem"
                      {:TableName table
                       :ReturnValues "ALL_NEW"
                       :Key (aws-serialize map-key)

                       :UpdateExpression "set #attr=:foo"
                       :ExpressionAttributeNames {"#attr" "test/foo"}
                       :ExpressionAttributeValues {":foo" {:S "AAAAAA"}}

                       ;; :AttributeUpdates (aws-serialize map-attrs)




                       #_
                       (aws-serialize map-attrs)

                       })
        {:keys [Attributes]}
        response

        ]

    (when Attributes
      (aws-deserialize Attributes))))

(comment

  (def -c (make-client ""
                       ""
                       ""
                       "ru-central1"))

  (def -r (get-item -c "table258" {:chat_id 1 :user_id 5}))

  (def -r (update-item -c "table258"
                       {:chat_id 1
                        :user_id 2}
                       {:aaa "AAA" :hello 123}))

  )
