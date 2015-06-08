(ns clojure-news.db
  (:require [matchbox.core :as m]
            [matchbox.atom :as matom]))

(def db-uri "http://luminous-torch-5788.firebaseio.com")

(def db-ref (m/connect db-uri))

;; Layout: {:ranks {name {:name, :rank}}}

(def rank-list
  (matom/sync-list
    (atom [])
    (-> db-ref
      (m/get-in :ranks)
      (m/order-by-child :count))))

(defn save-rank! [rank]
  (m/reset-in! db-ref [:ranks (:name rank)] rank))

(defn save-ranks! [ranks]
  (doseq [rank ranks]
    (save-rank! rank)))

(defn delete-all-rank-entries []
  (m/dissoc! db-uri :ranks))
