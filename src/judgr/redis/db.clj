(ns judgr.redis.db
  (:use [judgr.core]
        [judgr.db.base])
  (:import [java.util Date]
           [java.text SimpleDateFormat]
           [redis.clients.jedis Jedis]))

(defn- feature-key
  "Returns the key used to store information about a feature."
  [feature]
  (str "feature:" feature))

(defn- items-class
  "Returns the key used to store items of a given class."
  [class]
  (str "items:" (name class)))

(defn- class-count-field
  "Returns the field name used to store the number of times a feature was
flagged with a given class."
  [class]
  (str "total:" (name class)))

(defn- get-int
  "Returns an integer value from a feature hash."
  [feature field]
  (Integer/parseInt (get feature field "0")))

(defmacro with-transaction
  "Runs body inside a Redis transaction."
  [conn & body]
  `(let [~'conn (.multi ~'conn)]
     ~@body
     (.exec ~'conn)))

(defn- authenticate
  "Authenticates against the specified Redis connection."
  [redis-settings conn]
  (when (:auth? redis-settings)
    (.auth conn (:password redis-settings))))

(defn create-connection!
  "Creates a connection to Redis server."
  [{{:keys [redis]} :database}]
  (let [conn (Jedis. (:host redis) (:port redis))]
    (authenticate redis conn)
    conn))

(deftype RedisDB [settings conn]
  ConnectionBasedDB
  (get-connection [db]
    conn)

  FeatureDB
  (add-item! [db item class]
    (ensure-valid-class settings class
      (if (> (.sadd conn (items-class class) item) 0)
        {:item item :class class})))

  (add-feature! [db item feature class]
    (ensure-valid-class settings class
      (let [key (feature-key feature)
            class-count (class-count-field class)]
        (with-transaction conn
          (.sadd conn "features" feature)
          (.hincrBy conn key class-count 1)
          (.hincrBy conn key "total" 1))
        (.get-feature db feature))))

  (get-feature [db feature]
    (let [f (.hgetAll conn (feature-key feature))]
      (if-not (empty? f)
        (let [data {:feature feature :total (get-int f "total")}
              classes (:classes settings)]
          (reduce (fn [data class]
                    (let [v (get-int f (class-count-field class))]
                      (assoc-in data [:classes class] v)))
                  data classes)))))

  (count-features [db]
    (.scard conn "features"))

  (get-items [db]
    (let [classes (:classes settings)]
      (apply concat
             (map (fn [class]
                    (let [items (.smembers conn (items-class class))]
                      (map #(hash-map :item % :class class) items)))
                  classes))))

  (count-items [db]
    (let [classes (:classes settings)]
      (reduce + (map #(count-items-of-class db %) classes))))

  (count-items-of-class [db class]
    (.scard conn (items-class class))))

(defmethod db-from :redis [settings]
  (let [conn (create-connection! settings)]
    (RedisDB. settings conn)))