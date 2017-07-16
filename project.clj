(defproject lanyrd-embed "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojurescript "1.9.521" :exclusions [org.apache.ant/ant]]
                 [funcool/httpurr "1.0.0"]
                 [hiccups "0.3.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [trieloff/timbre-loggly "0.2.0"]
                 [org.clojure/clojure "1.8.0"]]
  :plugins [[lein-cljsbuild "1.1.6"]
            [lein-doo "0.1.7"]]
  :clean-targets ^{:protect false} ["target", "main.js"]
  :hooks [leiningen.cljsbuild]
  :cljsbuild {
    :builds [{:id "server-prod"
              :source-paths ["src/cljs"]
              :jar true
              :compiler {:main lanyrd-embed.core
                         :output-to "main.js"
                         :language-in  :ecmascript5
                         :language-out :ecmascript5
                         :pretty-print true
                         :closure-output-charset "US-ASCII" ;; USA! USA! USA! https://github.com/google/closure-compiler/issues/1704
                         :hashbang false
                         :optimizations :simple ;; notice this!
                         :target :nodejs }}

             {:id "server-test"
              :source-paths ["src/cljs" "src/test/cljs"]
              :compiler {:main lanyrd-embed.test-core
                         :output-to "tests.js"
                         :hashbang false
                         :optimizations :simple ;; notice this!
                         :target :nodejs }}]})
