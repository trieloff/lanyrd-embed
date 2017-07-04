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
    (trace "pairs" pairs)
    (identity mapped)))

(defn ical-data [url]
  (debug "Getting ics for event" url)
  (-> (http/get client (lanyrd-ical-url url))
      (p/then (fn [response]
                (p/resolved (parse-ical (:body response)))))
      (p/catch #(error % "Error retrieving ICS file"))))

(defn decode-body
  [response keywordize]
  (js->clj (js/JSON.parse (:body response)) :keywordize-keys keywordize))

(defn decode-json-rdf-body [response]
  (trace (decode-body response false))
  (apply merge (map (fn [[key val] pair] {(keyword (s/replace (s/replace key #".*#" "") #".*/" ""))
                                          (get (first val) "value")})
        (val (first (decode-body response false))))))

(defn schema-data [url]
  (debug "Getting schema for event" url)
  (-> (http/get client "http://getschema.org/microdataextractor" {:query-params {:url url :out "json"}})
      (p/then decode-json-rdf-body)
      (p/catch #(error % "Error retrieving microdata"))))

(defn decode-address [response]
  (trace "Decoding address" response)
  (let [decoded (-> (decode-body response true)
            :resourceSets
            first
            :resources
            first
            :address)]
    (debug "Decoded" decoded)
    (p/promise decoded)))

(defn get-loc-meta [key ical-loc]
  (println ical-loc)
  (p/map
    (partial merge ical-loc)
    (location-data (:lat ical-loc) (:lon ical-loc) key)))

(defn expand-loc [ical]
  (assoc ical
    :lat (first (s/split (:geo ical) #";"))
    :lon (last (s/split (:geo ical) #";"))))

(defn time-and-loc [url key]
  (p/chain (ical-data url)
           expand-loc
           (partial get-loc-meta key)))


(defn location-data
  ([lat lon key]
  (debug "Getting location data for " lat lon)
  (-> (http/get client (str "http://dev.virtualearth.net/REST/v1/Locations/" lat "," lon)
                {:query-params {:key key :o "json" :includeEntityTypes "PopulatedPlace"}})
      (p/then decode-address))))

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