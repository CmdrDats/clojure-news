(ns clojure-news.core
  (:import java.io.File
           java.net.URL)
  (:require [net.cgrand.enlive-html :as html]
            [clojure-news.db :as db]
            [clojure.string :as str]))

(def base-url "http://clojure-log.n01se.net/date/")

(def bot-names #{"clojurebot" "sexpbot" "lazybot"})

(def cache-folder-name "cache")

(defn cache-url
  "Cache URL result in filename, return file."
  [filename url]
  (let [cache-folder (File. cache-folder-name)
        file (File. cache-folder filename)]
    (when-not (.exists cache-folder)
      (.mkdir cache-folder))
    (when-not (.exists file)
      (spit file (apply str (html/emit* (html/html-resource (URL. url))))))
    file))

(defn get-log
  "Return HTML contents for remote file. Cached."
  [filename]
  (html/html-resource
   (cache-url filename (str base-url filename))))

(defn log-list
  "Return seq of all log archives from log index."
  []
  (let [index-file (cache-url "index.html" base-url)]
    (filter #(re-find #"^[0-9]{4}" %)
            (map html/text (html/select (html/html-resource index-file) [:a])))))

(defn cache-logs
  "Build local cache of all log archives."
  []
  (println "Starting cache")
  (doseq [filename (log-list)]
    (println "Caching:" filename)
    (cache-url filename (str base-url filename))))

;;

(defn parse-name [ln]
  (when-let [raw (or (first (:content (first (html/select ln [:em]))))
                (first (:content (first (html/select ln [:b])))))]
    (-> raw .trim (str/replace #":|^_*|\.|\[|\]|_*$" "") (str/replace "[" ""))))

(defn parse-time [ln]
  (html/text (first (html/select ln [:a]))))

(defn parse-separation [ln]
  (html/select ln [:.nh]))

(defn parse-text [ln]
  (.trim (html/text (last (:content ln)))))

;;

(defn parse-lines [log]
  (let [lines (html/select log [:p])]
    (let [names       (reductions #(or %2 %1) (map parse-name lines))
          times       (map parse-time lines)
          separations (map parse-separation lines)
          texts       (map parse-text lines)]
      (map vector separations times names texts))))

(defn cached? [f]
  (.exists (java.io.File. (str "cache/" f))))

(defn rank-logs
  "Caclulate map of names to ranking value."
  [files]
  (let [ranks (transient {})]
    (->> files
         (filter cached?)
         (map get-log)
         (mapcat #(html/select % [:p]))
         (map (juxt parse-name parse-text))
         (filter (comp seq last))
         (remove (comp bot-names first))
         (map (fn [[name text]] [name (.length text)]))
         (reduce (fn [[r last-name] [name length]]
                   (let [name (or name last-name)]
                     [(assoc! r name (+ length (get r name 0))) name]))
                 [(transient {}) nil])
         (first)
         (persistent!))))

(defn to-minutes
  "Convert timestamp to minutes (within the day)"
  [time]
  (let [[h m] (seq (.split time ":"))]
    (try
      (+ (* (Long/parseLong h) 60) (Long/parseLong m))
      (catch Exception e 0))))

(defn split-snippets
  "Build a seq of snippets, based on chunking all logs (for a day)"
  [log]
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

(defn score-snippet
  "Caclulate total score for snippet, from the rank of each user, for each line."
  [snippet ranks]
  (reduce + 0 (map #(get (get ranks (:name %)) :rank 0) snippet)))

(defn get-person
  "Lookup entry by name, return rank-1 stub otherwise"
  [name ranks]
  (get ranks name {:name name :rank 1}))

(defn best-snippet
  "Calculate best snippet (of the day)"
  [log ranks]
  (let [snippets (split-snippets log)
        best-snippet (reduce
                      (fn [[oldscore oldsnip] newsnip]
                        (let [newscore (score-snippet newsnip ranks)]
                          (if (> oldscore newscore)
                            [oldscore oldsnip]
                            [newscore newsnip])))
                      [0 []] snippets)]
    (second best-snippet)))


(defn headlines
  "Create summary of top 4 snippets for the day"
  [log ranks]
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

(defn print-snippet
  "Print snippet to console"
  [snippet]
  (doseq [[time name text] snippet]
    (println (str time "\t" name ":\t" text))))

(defn persist-ranks!
  "Save ranks map to db."
  [ranks]
  (println "Saving ranks")
  (let [existing (into {} (map (fn [{name :name :as rank}] [name rank]) @db/rank-list))]
    (doseq [[name count] ranks
            :let [ent (get existing name nil)]]
      (db/save-rank! (assoc ent :name name, :count count)))))

(defn get-rank
  "Calculate rank from count (sublinear)"
  [count]
  ;; Handy, DND level algorithm works perfect here...
  (min 69 (Math/floor (/ (+ 1 (Math/sqrt (+ (/ count 125) 1))) 2))))

(defn initial-setup []
  (db/save-ranks!
    (map (fn [[n c]] {:name n :rank (get-rank c) :count c})
         (rank-logs (log-list)))))

(defn rank-map []
  (into {} (map (fn [{n :name :as rank}] [n rank]) @db/rank-list)))


(comment
  (time
   (initial-setup))

  (defn calculate-snippet []
    (def date (rand-nth (vec (log-list))))
    (def log (get-log date))
    (def ranks (rank-map))
    (def snippet-value   (best-snippet log ranks))
    (def snippet-key (str/replace date ".html" ""))))
