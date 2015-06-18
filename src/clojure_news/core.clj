(ns clojure-news.core
  (:require [net.cgrand.enlive-html :as html]
            [clojure-news.db :as db]
            [clojure.string :as str]))

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

;;

(defn parse-name [ln]
  (when-let [raw (or (first (:content (first (html/select ln [:em]))))
                (first (:content (first (html/select ln [:b])))))]
    (-> raw .trim (str/replace ":" "") (str/replace #"_*$|^_*" ""))))

(defn parse-time [ln]
  (html/text (first (html/select ln [:a]))))

(defn parse-separation [ln]
  (html/select ln [:.nh]))

(defn parse-text [ln]
  (.trim (html/text (last (:content ln)))))

;;

(defn rank-line [ranks name text]
  (if (contains? bot-names name)
    ranks
    (let [current-rank (get ranks name 0)]
      (assoc ranks name (+ current-rank (.length text))))))

(defn parse-lines [log]
  (let [lines (html/select log [:p])]
    (let [names       (reductions #(or %2 %1) (map parse-name lines))
          times       (map parse-time lines)
          separations (map parse-separation lines)
          texts       (map parse-text lines)]
      (map vector separations times names texts))))

(defn rank-logs [files]
  (let [ranks (atom {})]
    (doseq [f files]
      (when (.exists (java.io.File. (str "cache/" f)))
        (println "Ranking" f)
        (doseq [[_ _ name text] (parse-lines (get-log f))]
          (when (seq text)
            (swap! ranks rank-line name text)))))
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
    (doseq [[sep time name text] (parse-lines log)]
      (do
        (let [tm (to-minutes time)
              t (- tm @last-time)]
          (when (and (> @last-time 0) (> t 10))
            (when @snippet
              (swap! snippets conj @snippet))
            (compare-and-set! snippet @snippet []))
          (compare-and-set! last-time @last-time tm))
        (swap! snippet conj {:time time :name name :text text})))
    (if (> (count @snippet) 0)
      (swap! snippets conj @snippet))))

(defn score-snippet [snippet ranks]
  (reduce #(+ %1 (get (get ranks (:name %2)) :rank 0)) 0 snippet))

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


(comment
  (initial-setup)
  (def date (rand-nth (vec (log-list))))
  (def log (get-log date))
  (def ranks (rank-map))
  (def snippet-value   (best-snippet log ranks))
  (count snippet-value)
  (def snippet-key (str/replace date ".html" "")))


(rand-nth [1 2 3])
