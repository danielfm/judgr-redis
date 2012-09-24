(ns judgr.redis.db
  (:use [judgr.core]
        [judgr.db.base])
  (:import [redis.clients.jedis JedisPoolConfig JedisPool Jedis]))

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

(defmacro with-connection
  ""
  [pool settings & body]
  `(let [~'conn (.getResource ~pool)]
     (try
       (.select ~'conn (or (-> ~settings :database :redis :database) 0))
       ~@body
       (finally
         (.returnResource  ~pool ~'conn)))))

(defn create-pool!
  ""
  [{{:keys [redis]} :database}]
  (let [config (JedisPoolConfig.)]
    (JedisPool. config (:host redis) (:port redis) 1000 (:password redis))))

(deftype RedisDB [settings pool]
  ConnectionBasedDB
  (get-connection [db]
    pool)

  FeatureDB
  (add-item! [db item class]
    (ensure-valid-class settings class
      (with-connection pool settings
        (if (> (.sadd conn (items-class class) item) 0)
          {:item item :class class}))))

  (add-feature! [db item feature class]
    (ensure-valid-class settings class
      (let [key (feature-key feature)
            class-count (class-count-field class)]
        (with-connection pool settings
          (with-transaction conn
            (.sadd conn "features" feature)
            (.hincrBy conn key class-count 1)
            (.hincrBy conn key "total" 1)))
        (.get-feature db feature))))

  (clean-db! [db]
    (with-connection pool settings
      (.flushDB conn)))

  (get-feature [db feature]
    (with-connection pool settings
      (let [f (.hgetAll conn (feature-key feature))]
        (if-not (empty? f)
          (let [data {:feature feature :total (get-int f "total")}
                classes (:classes settings)]
            (reduce (fn [data class]
                      (let [v (get-int f (class-count-field class))]
                        (assoc-in data [:classes class] v)))
                    data classes))))))

  (count-features [db]
    (with-connection pool settings
      (.scard conn "features")))

  (get-items [db]
    (with-connection pool settings
      (let [classes (:classes settings)]
        (apply concat
               (map (fn [class]
                      (let [items (.smembers conn (items-class class))]
                        (map #(hash-map :item % :class class) items)))
                    classes)))))

  (count-items [db]
    (with-connection pool settings
      (let [classes (:classes settings)]
        (reduce + (map #(count-items-of-class db %) classes)))))

  (count-items-of-class [db class]
    (with-connection pool settings
      (.scard conn (items-class class)))))

(defmethod db-from :redis [settings]
  (let [pool (create-pool! settings)]
    (RedisDB. settings pool)))