(defproject clojure-news "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.7.0-RC1"]
                 [enlive "1.0.1" :exclusions [org.clojure/clojure]]
                 [matchbox "0.0.7-SNAPSHOT"]
                 [org.clojure/clojurescript "0.0-3308"]
                 [reagent "0.5.0"]]
  :profiles {:dev {:plugins [[lein-cljsbuild "1.0.5"]
                             [lein-figwheel "0.3.1"]]}}

  :figwheel
  {:server-port      3450
   :nrepl-port       7888
   :css-dirs         ["resources/public/css"]
   :http-server-root ""}

  :cljsbuild
  {:builds
   [{:id "dev"
     :source-paths ["src"]
     :figwheel {}
     :compiler
     {:main "clojure-news.main"
      :pretty-print true
      :asset-path "js"
      :output-to "resources/public/js/main.js"
      :output-dir "resources/public/js"
      :optimizations :none
      :source-map "resources/public/js/main.js.map"
      :cache-analysis true}}

    {:id "prod"
     :source-paths ["src"]
     :compiler
     {:main "clojure-news.main"
      :pretty-print false
      :asset-path "js"
      :output-to "resources/public/js/main.js"
      :output-dir "target/js"
      :optimizations :advanced
      :cache-analysis true}}]})
