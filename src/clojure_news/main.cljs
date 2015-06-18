(ns clojure-news.main
  (:require
    [matchbox.reagent :as r]
    [reagent.core :as reagent]
    [clojure-news.db :as db]))

(defn person [{:keys [name rank]}]
  [:div.nameblock [:div.rank [:div {:class (str "rank-" rank)}]] "Hi, I am " name ", rank: " rank])

(defn leaderboard []
  [:div
   (for [ch @db/rank-list]
     ^{:key (:name ch)}
     [person ch]
     )])


(defn mountit []
  (reagent/render-component
    [leaderboard]
    (.-body js/document)))

(mountit)
#_(db/save-rank! {:name "Blah blah"})

#_(set! (.-innerHTML (.getElementById js/document "app")) "<h1>Good morning</h1>")


