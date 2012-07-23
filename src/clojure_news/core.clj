(ns clojure-news.core
  (:require [noir.core :as noir])
  (:require [net.cgrand.enlive-html :as html])
  (:require [noir.server :as server]))

(def base-url "http://clojure-log.n01se.net/date/")

(defn cache-url [filename url]
  (let [cache-folder (java.io.File. "cache")
        file (java.io.File. cache-folder filename)]
    (when-not (.exists cache-folder)
      (.mkdir cache-folder))
    
    (when-not (.exists file)
      (spit file (apply str (html/emit* (html/html-resource (java.net.URL. url))))))
    file))

(defn get-log [filename]
  (html/html-resource
   (cache-url filename (str base-url filename))))

(defn log-list []
  (filter #(re-find #"^[0-9]{4}" %) (map html/text (html/select (html/html-resource (cache-url "index.html" base-url)) [:a]))))

(defn cache-logs []
  (println "Starting cache")
  (doseq [filename (log-list)]
    (println "Caching:" filename)
    (cache-url filename (str base-url filename))))

(defn run-log [line-fn log]
  (let [lines (html/select log [:p])]
    (loop [ln (first lines)
           ot (rest lines)
           person nil]
      (let [time (html/text (first (html/select ln [:a])))
            seperation (html/select ln [:.nh])
            name (or (first (:content (first (html/select ln [:em]))))
                     (first (:content (first (html/select ln [:b]))))
                     person)
            text (html/text (last (:content ln)))]
        (when (> (.length text) 0)
          (line-fn seperation time (.replace (.trim name) ":" "") (.trim text)))
        (when ln
          (recur (first ot) (rest ot) name))))))

(defn rank-line [ranks name text]
  (let [current-rank (get ranks name 0)]
    (assoc ranks name (+ current-rank (.length text)))))

(defn rank-logs [files]
  (let [ranks (atom {})]
    (doseq [f files]
      (when (.exists (java.io.File. (str "cache/" f)))
        (println "Ranking" f)
        (run-log
         (fn [_ _ name text]
           (swap! ranks rank-line name text))
         (get-log f))))
    (into {} (take 50 (reverse (sort-by second @ranks))))))

(defn to-minutes [time]
  (let [[h m] (seq (.split time ":"))]
    (try
      (+ (* (Long/parseLong h) 60) (Long/parseLong m))
      (catch Exception e 0))))

(defn split-snippets [log]
  (let [last-time (atom 0)
        snippet (atom [])
        snippets (atom [])]
    (run-log (fn [sep time name text]
               (let [tm (to-minutes time)
                     t (- tm @last-time)]
                 (when (and (> @last-time 0) (> t 10))
                   (when @snippet
                     (swap! snippets conj @snippet))
                   (compare-and-set! snippet @snippet []))
                 (compare-and-set! last-time @last-time tm))
               (swap! snippet conj [time name text])
               ) log)
    (if (> (count @snippet) 0)
      (swap! snippets conj @snippet))))

(defn score-snippet [snippet ranks]
  (reduce #(+ %1 (get ranks (second %2) 0)) 0 snippet))

(defn print-snippet [snippet]
  (doseq [[time name text] snippet]
    (println (str time "\t" name ":\t" text))))


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

(html/defsnippet leaderentry "public/daily.html" [:.leaderboard [:li (html/nth-of-type 1)]] [person]
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
  [:.leaderboard] (html/content (leaderboard leaders))
  [:.best-snippet] (html/content (snippet best-snippet))
  [:.past-snippet] (html/content (snippet past-snippet)))

(noir/defpage "/test" []
  (daily-email [{:person {:name "hello" :rank 4} :title "title" :link "lnk" :participants [{:name "rhickey" :rank 69}]}] [{:name "hello" :rank 4} {:name "rhickey" :rank 69}] {:person {:name "rhickey" :rank 69} :title "foo" :link "morelnk" :log [{:person {:name "rhickey" :rank 69} :time "3:45" :text "Why, what now?"}]} []))

(def srv (atom nil))

(defn start-server []
  (compare-and-set! srv @srv (server/start 8080)))