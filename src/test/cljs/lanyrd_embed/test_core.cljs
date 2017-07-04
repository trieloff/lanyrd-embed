(ns lanyrd-embed.test-core
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [lanyrd-embed.core :as c]))


(deftest test-urls
  (is (c/is-lanyrd-url "http://lanyrd.com/2017/smashingconf-freiburg/"))
  (is (not (c/is-lanyrd-url "http://example.com/2017/smashingconf-freiburg/"))))

(deftest test-numbers
  (is (= 1 1)))

(deftest test-error
  (is (not (nil? (c/oembed-error "http://www.example.com")))))

(enable-console-print!)
(cljs.test/run-tests)