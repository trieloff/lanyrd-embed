(ns lanyrd-embed.test-core
  (:require [cljs.test :refer-macros [deftest is testing run-tests async]]
            [promesa.core :as p]
            [cljs.nodejs :as node]
            [lanyrd-embed.core :as c]
            [clojure.string :as s]))

(defn env
  "Returns the value of the environment variable k,
   or raises if k is missing from the environment."
  [k]
  (aget (.-env node/process) k))

(deftest test-urls
  (is (c/is-lanyrd-url "http://lanyrd.com/2017/smashingconf-freiburg/"))
  (is (not (c/is-lanyrd-url "http://example.com/2017/smashingconf-freiburg/"))))

(deftest test-meta
  (is (= 2017 (:year (c/lanyrd-url-meta "http://lanyrd.com/2017/smashingconf-freiburg/"))))
  (is (= "smashingconf-freiburg" (:id (c/lanyrd-url-meta "http://lanyrd.com/2017/smashingconf-freiburg/")))))

(deftest ical-url
  (is (= "http://lanyrd.com/2017/strange-loop/strange-loop.ics" (c/lanyrd-ical-url "http://lanyrd.com/2017/strange-loop/"))))

(deftest test-numbers
  (is (= 1 1)))

(deftest test-error
  (is (not (nil? (c/oembed-error "http://www.example.com")))))

(deftest test-parse-ical
  (async done
    (-> (c/ical-data "http://lanyrd.com/2017/strange-loop/")
        (p/then #((is (= (:summary %) "Strange Loop 2017"))
                   (done)
                   (identity %)))
        (p/catch #()))))

(deftest test-parse-schema
  (async done
    (-> (c/schema-data "http://lanyrd.com/2017/jeffconf/")
        (p/then #((is (= (:name %) "JeffConf"))
                   (done)
                   (identity %)))
        (p/catch #()))))

(deftest test-get-location
  (is (< 0 (count (env "BING_MAPS_KEY"))) "BING_MAPS_KEY environment variable must be set. Get your key at https://www.bingmapsportal.com/")
  (async done
    (-> (c/location-data 47.9949575609 7.85275154418 (env "BING_MAPS_KEY"))
        (p/then #((is (= (:locality %) "Freiburg"))
                   (done)
                   (identity %)))
        (p/catch #()))))


(deftest test-time-and-loc
  (is (< 0 (count (env "BING_MAPS_KEY"))) "BING_MAPS_KEY environment variable must be set. Get your key at https://www.bingmapsportal.com/")
  (async done
    (p/catch (p/then (c/time-and-loc "http://lanyrd.com/2017/strange-loop/" (env "BING_MAPS_KEY"))
             #((is (= (:locality %) "St Louis"))
                (is (= (:summary %) "Strange Loop 2017"))
                (done)
                (identity %)))
             #())))


(deftest test-collect-all
  (is (< 0 (count (env "BING_MAPS_KEY"))) "BING_MAPS_KEY environment variable must be set. Get your key at https://www.bingmapsportal.com/")
  (async done
    (p/catch (p/then (c/collect-data "http://lanyrd.com/2017/strange-loop/" (env "BING_MAPS_KEY"))
                     #((is (= (:locality %) "St Louis"))
                        (is (= (:summary %) "Strange Loop 2017"))
                        (is (= (:startDate %) "Sept. 28, 2017"))
                        (println (keys %))
                        (done)
                        (identity %)))
             #())))

(deftest test-timestamp
  (is (= "2017-07-28" (c/to-datetime "20170728"))))

(deftest test-render
  (let [data {:description "Example conference for testing"
              :localty "Testsville"
              :name "TestConf"
              :startDate "July 4th, 2017"
              :endDate "July 7th, 2017"
              :url "http://www.testconf.com"
              :lat 0.12
              :lon 3.14159
              :formattedAddress "Testsville, TA, United States"
              :dtstart "00000000"
              :dtend "00000000"}
        rendered (c/render-html data)]
    (println rendered)
    (is (< 0 (count rendered)))))

(enable-console-print!)
(cljs.test/run-tests)
