(ns clojure-news.db
  #?(:cljs (:refer-clojure :exclude [atom]))
  (:require
    [matchbox.core :as m]
    [matchbox.atom :as a]
    #?(:cljs [reagent.core :refer [atom]])))

(def db-uri "http://luminous-torch-5788.firebaseio.com")

(def db-ref (m/connect db-uri))

;; Layout: {:ranks {name {:name, :rank}}}
(defonce rat (atom nil))

(def rank-list
  (a/sync-list
    rat
    (-> db-ref
      (m/get-in :ranks)
      (m/order-by-child :rank)
      (m/take-last 10))))

(defonce pat (atom nil))

(def past-snippets
  (a/sync-list
    pat
    (-> db-ref
      (m/get-in :snippets))))

(defn save-rank! [rank]
  (m/reset-in! db-ref [:ranks (:name rank)] rank))

(defn save-ranks! [ranks]
  (doseq [rank ranks]
    (save-rank! rank)))

(m/reset-in! db-ref [:ranks :chris-truter] nil)

(defn delete-all-rank-entries []
  (m/dissoc! db-ref :ranks))


(comment
  (def users (mapv :name @rank-list))
  (dotimes [i 1000]
    (Thread/sleep 500)
    (dotimes [i 50]
      (m/reset-in! db-ref [:ranks :deon-moolman] {:name "CmdrDats" :rank (inc (rand-int 69))})
      (m/reset-in! db-ref [:ranks (rand-nth users) :rank] (inc (rand-int 69)))))
  (m/reset-in! db-ref [:snippets clojure-news.core/snippet-key :best] clojure-news.core/snippet-value))
