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
            [timbre.loggly :as loggly]
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
  (debug (decode-body response false))
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

(defn location-data
  ([lat lon key]
   (debug "Getting location data for " lat lon)
   (-> (http/get client (str "http://dev.virtualearth.net/REST/v1/Locations/" lat "," lon)
                 {:query-params {:key key :o "json" :includeEntityTypes "PopulatedPlace"}})
       (p/then decode-address))))

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

(defn collect-data [url key]
  (p/map
    (partial apply merge)
    (p/all [(time-and-loc url key)
           (schema-data url)])))

;;(:description
;; :locality
;; :countryRegion
;; :adminDistrict2
;; :method
;; :uid
;; :name
;; :adminDistrict
;; :geo
;; :startDate
;; :type
;; :summary
;; :begin
;; :endDate
;; :dtend
;; :lon
;; :x-ms-olk-forceinspectoropen
;; :url
;; :x-wr-calname
;; :lat
;; :dtstart
;; :formattedAddress
;; :prodid
;; :x-original-url
;; :end
;; :version
;; :location)

(defn to-datetime [tstamp]
  (s/join "-" (rest (re-find #"([0-9]{4})([0-9]{2})([0-9]{2})" tstamp))))

(defn render-html [{:keys [description locality name startDate endDate url lat lon formattedAddress dtstart dtend]}]
  (debug description locality name startDate endDate url lat lon formattedAddress dtstart dtend)
  (html [:a {:class "url", :href url}
         [:time {:datetime (to-datetime dtstart), :class "dtstart"} startDate]", "
         [:time {:datetime (to-datetime dtend), :class "dtend"} " " endDate]
         [:span {:class "summary"} name]" in "
         [:span {:class "location"} formattedAddress]]
        [:div {:class "description"} description]))

(defn render-json [data]
  (let [{:keys [description locality name startDate endDate url lat lon formattedAddress dtstart dtend]} data]
    {:version "1.0"
     :type "rich"
     :title name
     :url url
     :provider_name "Lanyrd"
     :provider_url "http://lanyrd.com/"
     :html (render-html data)}))

(defn oembed-error [url]
  (error "Invalid Lanyrd event" url)
  {:error (str url " " "is not a valid Lanyrd event")})

(defn embed-lanyrd [url key]
  (if (is-lanyrd-url url)
    (p/map #(render-json %)
           (collect-data url key))
    (oembed-error url)))

(defn main [params]
      (let [safe-params (dissoc params :key)]
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
             (embed-lanyrd (:url params) (:key params)))))

(defn clj-promise->js [o]
      (if (p/promise? o)
        (p/then o (fn [r] (p/resolved (clj->js r))))
        (clj->js o)))

(set! js/main (fn [args] (clj-promise->js (main (js->clj args :keywordize-keys true)))))