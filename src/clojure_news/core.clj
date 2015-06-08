(ns clojure-news.core
  (:require [net.cgrand.enlive-html :as html]
            [clojure-news.db :as db]))

(def base-url "http://clojure-log.n01se.net/date/")
(def bot-names #{"clojurebot" "sexpbot" "lazybot"})

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
  (if (contains? bot-names name)
    ranks
    (let [current-rank (get ranks name 0)]
      (assoc ranks name (+ current-rank (.length text))))))

(defn rank-logs [files]
  (let [ranks (atom {})]
    (doseq [f files]
      (when (.exists (java.io.File. (str "cache/" f)))
        (println "Ranking" f)
        (run-log
         (fn [_ _ name text]
           (swap! ranks rank-line name text))
         (get-log f))))
     @ranks))

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
  (reduce #(+ %1 (get (get ranks (second %2)) :rank 0)) 0 snippet))

(defn get-person [name ranks]
  (get ranks name {:name name :rank 1}))

(defn best-snippet [log ranks]
  (let [snippets (split-snippets log)
        best-snippet (reduce
                      (fn [[oldscore oldsnip] newsnip]
                        (let [newscore (score-snippet newsnip ranks)]
                          (if (> oldscore newscore)
                            [oldscore oldsnip]
                            [newscore newsnip])))
                      [0 []] snippets)]
    (second best-snippet)))


(defn headlines [log ranks]
  (let [snippets (split-snippets log)
        sorted (reverse (sort-by #(score-snippet % ranks) snippets))]
    (for [snip (take 4 sorted)
          :let [head (first snip)]]
      {:title (nth head 2)
       :time (first head)
       :person (get-person (second head) ranks)
       :participants
       (reduce (fn [people line] (conj people
                                      (get-person (second line) ranks))) #{} snip)})))

(defn print-snippet [snippet]
  (doseq [[time name text] snippet]
    (println (str time "\t" name ":\t" text))))

(defn persist-ranks! [ranks]
  (println "Saving ranks")
  (let [existing (into {} (map (fn [{name :name :as rank}] [name rank]) @db/rank-list))]
    (doseq [[name count] ranks
            :let [ent (get existing name nil)]]
      (db/save-rank! (assoc ent :name name, :count count)))))

(defn get-rank [count]
  ;; Handy, DND level algorithm works perfect here...
  (min 69 (Math/floor (/ (+ 1 (Math/sqrt (+ (/ count 125) 1))) 2))))

(defn initial-setup []
  (db/save-ranks!
    (map (fn [[n c]] {:name n :rank (get-rank c) :count c})
         (rank-logs (log-list)))))

(defn rank-map []
  (into {} (map (fn [{n :name :as rank}] [n rank]) @db/rank-list)))

(defn rank-top [n]
  (take n (reverse @db/rank-list)))
