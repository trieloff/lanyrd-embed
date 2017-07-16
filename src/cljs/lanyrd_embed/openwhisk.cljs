(ns lanyrd-embed.openwhisk
  (:require [promesa.core :as p]))

(def owhisk (js/require "openwhisk"))

(defn list-actions [& options]
  (let [ow (.owhisk (clj->js options))]
    (.list (.-actions ow))))

(defn clj-promise->js [o]
  (if (p/promise? o)
    (p/then o (fn [r] (p/resolved (clj->js r))))
    (clj->js o)))

(defn wrapfn [cljsfunc]
  "Wraps and exports a ClojureScript function as OpenWhisk main function"
  (set! js/main (fn [args] (clj-promise->js (cljsfunc (js->clj args :keywordize-keys true))))))
