(ns lanyrd-embed.test-core
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [lanyrd-embed.core :as c]))

(def htmlparser (js/require "htmlparser"))


(deftest test-urls
  (is (c/is-lanyrd-url "http://lanyrd.com/2017/smashingconf-freiburg/"))
  (is (not (c/is-lanyrd-url "http://example.com/2017/smashingconf-freiburg/"))))

(deftest test-meta
  (is (= 2017 (:year (c/lanyrd-url-meta "http://lanyrd.com/2017/smashingconf-freiburg/"))))
  (is (= "smashingconf-freiburg" (:id (c/lanyrd-url-meta "http://lanyrd.com/2017/smashingconf-freiburg/")))))

(deftest test-numbers
  (is (= 1 1)))

(deftest test-error
  (is (not (nil? (c/oembed-error "http://www.example.com")))))

(enable-console-print!)
(cljs.test/run-tests)


(println (.DefaultHandler htmlparser (fn [error dom] (println dom))))

;; http://getschema.org/microdataextractor?url=http%3A%2F%2Flanyrd.com%2F2017%2Fjupytercon%2F&out=json
;; http://lanyrd.com/2017/strange-loop/strange-loop.ics
;; GET http://dev.virtualearth.net/REST/v1/Locations/47.9949575609,7.85275154418?o=json&includeEntityTypes=PopulatedPlace&includeNeighborhood=1&key=…