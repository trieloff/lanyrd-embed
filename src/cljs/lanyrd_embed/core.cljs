(ns lanyrd-embed.core
  (:require-macros [hiccups.core :as hiccups :refer [html]])
  (:require [cljs.core :as core]
            [cljs.nodejs :as nodejs]
            [httpurr.client :as http]
            [promesa.core :as p]
            [hiccups.runtime :as hiccupsrt]
            [taoensso.timbre :as timbre
             :refer-macros [log  trace  debug  info  warn  error  fatal  report
                            logf tracef debugf infof warnf errorf fatalf reportf
                            spy get-env]]
            [lanyrd-embed.loggly :as loggly]
            [httpurr.client.node :refer [client]]))

(defn is-lanyrd-url [url]
  (not (nil? (re-matches #"https?://lanyrd.com/[0-9]{4}/.*/" url))))

(defn embed-lanyrd [params]
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
             (embed-lanyrd params))))

(defn clj-promise->js [o]
      (if (p/promise? o)
        (p/then o (fn [r] (p/resolved (clj->js r))))
        (clj->js o)))

(set! js/main (fn [args] (clj-promise->js (main (js->clj args :keywordize-keys true)))))