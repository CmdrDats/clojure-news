(ns clojure-news.web
  (:require [noir.core :as noir])
  (:require [net.cgrand.enlive-html :as html])
  (:require [clojure-news.sql :as sql])
  (:require [clojure-news.core :as core])
  (:require [noir.server :as server]))

;; == Web pages
(html/defsnippet toc-participant "public/daily.html" [:.contents :.tocentry [:.participants (html/nth-of-type 1)] :> html/first-child] [participant]
  [:.name] (html/content (:name participant))
  [:.rank] (html/do->
            (html/remove-attr :class)
            (html/add-class (str "small-rank-" (:rank participant)) "rank"))
  )

(html/defsnippet toc-entry "public/daily.html" [:.contents [:.tocentry (html/nth-of-type 1)]] [person title link participants]
  [:.title] (html/content title)
  [:.rank] (html/do->
             (html/remove-attr :class)
             (html/add-class (str "small-rank-" (:rank person)) "rank"))
  [:.name] (html/content (:name person))
  [:.toclink] (html/set-attr :href link)
  [:.participants] (html/content (map toc-participant participants)))

(html/defsnippet leaderentry "public/daily.html" [:.leaderboard :ol [:li (html/nth-of-type 1)]] [person]
  [:.rank] (html/do->
            (html/remove-attr :class)
            (html/add-class (str "small-rank-" (:rank person)) "rank"))
  [:.name] (html/content (:name person))
  )

(html/defsnippet leaderboard "public/daily.html" [:.leaderboard] [leaders]
  [:ol] (html/content (map leaderentry leaders)))

(html/defsnippet chat-entry "public/daily.html" [:.best-snippet [:.chat (html/nth-of-type 1)]] [{:keys [person time text]}]
  [:.rank] (html/do->
            (html/remove-attr :class)
            (html/add-class (str "small-rank-" (:rank person)) "rank"))
  [:.name] (html/content (:name person))
  [:.time] (html/content time)
  [:.text] (html/content text)
  )

(html/defsnippet snippet "public/daily.html" [:.best-snippet] [{:keys [person title link log] :as snippet}]
  [:.heading :.name] (html/content (:name person))
  [:.heading :.rank] (html/do->
                      (html/remove-attr :class)
                      (html/add-class (str "small-rank-" (:rank person)) "rank"))
  [:.heading :.title] (html/content title)
  [:.snippetlink] (html/set-attr :href link)
  [:.chatlog] (html/content (map chat-entry log)))

(html/deftemplate daily-email "public/daily.html" [toc leaders best-snippet past-snippet]
  [:.contents] (html/content (map #(toc-entry (:person %) (:title %) (:link %) (:participants %)) toc))
  [:.leaderboard] (html/substitute (leaderboard leaders))
  [:.best-snippet] (html/substitute (snippet best-snippet))
  [:.past-snippet] (html/substitute (snippet past-snippet)))

(defn convert-snippet [snippet ranks]
  (let [title (first snippet)]
    {:title (get title 2)
     :person (core/get-person (second title) ranks)
     :log (map (fn [line]
                 {:person (core/get-person (get line 1) ranks)
                  :time (get line 0)
                  :text (get line 2)}) snippet)}))

(noir/defpage "/test" []
  (let [ranks (core/rank-map)
        log (core/get-log "2012-07-24.html")
        best-snippet (convert-snippet (core/best-snippet log ranks) ranks)
        headlines (core/headlines log ranks)]
    (daily-email headlines (core/rank-top 10) best-snippet [])
    )
  )

(def srv (atom nil))

(defn start-server []
  (compare-and-set! srv @srv (server/start 8080)))