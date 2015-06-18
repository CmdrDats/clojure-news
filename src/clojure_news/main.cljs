(ns clojure-news.main
  (:require
    [matchbox.reagent :as r]
    [reagent.core :as reagent]
    [clojure-news.db :as db]))

(defn person [{:keys [name rank]}]
  [:div.nameblock [:div.rank [:div {:class (str "rank-" rank)}]] "I am " name ", Rank: " rank])

(defn leaderboard []
  [:div
   (for [[n ch] @db/rank-list]
     ^{:key (:name ch)}
     [person ch]
     )])

(defn snippet [dt snippet]
  (into [:div [:h2 (name dt)]]
    (for [{:keys [name time text]} (:best snippet)]
      [:div [:div {:class (str "small-rank-" (get-in @db/rank-list [(keyword name) :rank]))}]
       [:span time " "] [:b name " :: "] [:span text " "]])))

(defn show-snippets []
  (into [:div]
    (for [[dt sn] @db/past-snippets]
      [:div [snippet dt sn] [:hr]])))

(defn mountit []
  (reagent/render-component
    [:div
     [show-snippets]
     [:hr]
     [leaderboard]]
    (.-body js/document)))

(mountit)
#_(db/save-rank! {:name "Blah blah"})

#_(set! (.-innerHTML (.getElementById js/document "app")) "<h1>Good morning</h1>")


