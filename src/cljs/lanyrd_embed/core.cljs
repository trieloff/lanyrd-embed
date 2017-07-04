(ns lanyrd-embed.core
  (:require-macros [hiccups.core :as hiccups :refer [html]])
  (:require [cljs.core :as core]
            [cljs.nodejs :as nodejs]
            [httpurr.client :as http]
            [promesa.core :as p]
            [hiccups.runtime :as hiccupsrt]
            [clojure.string :as s]
            [taoensso.timbre :as timbre
             :refer-macros [log  trace  debug  info  warn  error  fatal  report
                            logf tracef debugf infof warnf errorf fatalf reportf
                            spy get-env]]
            [lanyrd-embed.loggly :as loggly]
            [httpurr.client.node :refer [client]]))

(defn is-lanyrd-url [url]
  (not (nil? (re-matches #"https?://lanyrd.com/[0-9]{4}/.*/" url))))

(defn lanyrd-url-meta [url]
  (let [fragments (re-find #"https?://lanyrd.com/([0-9]{4})/(.*)/" url)]
    {:url  url
     :year (js/parseInt (nth fragments 1))
     :id   (nth fragments 2)}))

(defn lanyrd-ical-url [url]
  (let [{:keys [year id]} (lanyrd-url-meta url)]
    (str "http://lanyrd.com/" year "/" id "/" id ".ics")))

(defn parse-ical [event]
  (let [lines (s/split-lines event)
        pairs (map #(s/split % #":" 2) lines)
        mapped (reduce #(assoc %1
                          (keyword (s/lower-case (s/replace-first (first %2) #";.*" "")))
                          (s/replace (s/replace (last %2) #"\\n" "\n") #"\\," ",")) {} pairs)]
    (debug "pairs" pairs)
    (identity mapped)))

(defn ical-data [url]
  (debug "Getting ics for event" url)
  (p/then (http/get client
                    (lanyrd-ical-url url))
          (fn [response]
            (p/resolved (parse-ical (:body response))))))

(defn embed-lanyrd [url]
  (if (is-lanyrd-url url)
    (do
      (debug "embedding" url))
    (oembed-error url)))

(defn oembed-error [url]
  (error "Invalid Lanyrd event" url)
  {:error (str url " " "is not a valid Lanyrd event")})

(defn main [params]
      (let [safe-params (dissoc params :key :loggly)]
           (timbre/merge-config! {:level :debug})
           (debug params "Logger activated.")
           (if (not (nil? (:loggly params)))
             (timbre/merge-config! {:appenders {:loggly (loggly/loggly-appender {:tags [:oembed :lanyrd :timbre]
                                                                                 :token (:loggly params)})}}))
           (if (nil? (:url params))
             (do
               (warn safe-params "No URL provided")
               {:version "1.0"
                :params  safe-params
                :error   "You need to specify a URL to embed. Use the `url` parameter."})
             (embed-lanyrd (:url params)))))

(defn clj-promise->js [o]
      (if (p/promise? o)
        (p/then o (fn [r] (p/resolved (clj->js r))))
        (clj->js o)))

(set! js/main (fn [args] (clj-promise->js (main (js->clj args :keywordize-keys true)))))