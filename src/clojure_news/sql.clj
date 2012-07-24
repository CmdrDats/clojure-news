(ns clojure-news.sql
  (:require [clojure.java.jdbc :as sql]))

(def db
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     "db/database.db"
   })

(defmacro with-db [& body]
  `(sql/with-connection db ~@body))

(defn create-db []
  (.mkdir (clojure.java.io/file "db"))
  (try (with-db
         (sql/create-table :ranks
                           [:id "INTEGER PRIMARY KEY AUTOINCREMENT"]
                           [:name :text]
                           [:rank :int]
                           [:count :int])
         )
       
       (catch Exception e (println e) (.printStackTrace e))))

(defn rank-list []
  (sql/with-query-results rs ["select * from ranks order by count"] (doall rs)))


(defn save-rank-entry [rank]
  (sql/update-or-insert-values :ranks ["id=?" (:id rank)] rank))

(defn save-rank-entries [ranks]
  (apply sql/insert-rows :ranks (map (fn [{:keys [name rank count]}] [nil name rank count]) ranks))
  )

(defn delete-all-rank-entries []
  (sql/delete-rows :ranks ["id>?" 0]))













