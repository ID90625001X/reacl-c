(ns reacl-c.test-util.xpath
  (:require [reacl2.test-util.xpath :as rp]
            [reacl-c.core :as c]
            [reacl-c.base :as base]
            [reacl-c.impl.reacl :as impl])
  (:refer-clojure :exclude [and or contains? nth nth-last comp first last range]))

(defn named [n]
  (cond
    (string? n) (rp/tag n)
    (some? (c/meta-name-id n)) (rp/class (impl/named (c/meta-name-id n)))
    (base/name-id? n) (rp/class (impl/named n))
    :else (assert false n)))

(def attr rp/attr)

(def and rp/and)
(def or rp/or)
(def range rp/range)
(def nth rp/nth)
(def nth-last rp/nth-last)
(def first rp/first)
(def last rp/last)
(def root rp/root)
(def text rp/text)
(def state rp/app-state)
(def all rp/all)
(def self rp/self)
(def parent rp/parent)
(def children rp/children)

(def where rp/where)
(def is? rp/is?)
(def is= rp/is=)
(def id= rp/id=)
(def css-class? rp/css-class?)
(def style? rp/style?)

(def select-all rp/select-all)
(def select rp/select)
(def select-one rp/select-one)
(def contains? rp/contains?)

(defn comp
  "Compose the given xpath selector forms to a combined
  selector, where from left to right, the selectors restrict the filter
  further. \n
  Valid selectors are all the primitives from this module,
  as well as:\n

  - strings stand for a virtual dom nodes
  - named item or the function creating named item, stand for such items.
  - keywords stand for attribute names of dom nodes as with [[attr]]\n

  Also see [[>>]] for a convenience macro version of this."

  [& selectors]
  ;; TODO: maybe insert a 'skip all wrappers' after every selector to hide them? (which makes this a pure 'visual xpath'?!)
  (apply rp/comp (map (fn [v]
                        (cond
                          (string? v) (named v)
                          (base/name-id? v) (named v)
                          ;; (var? v) (named-var v)
                          (keyword? v) (attr v)
                          (some? (c/meta-name-id v)) (named v)
                          (base/named? v) (named (base/named-name-id v))
                          ;; TODO: add all other items??!!
                          :else v))
                      selectors)))
