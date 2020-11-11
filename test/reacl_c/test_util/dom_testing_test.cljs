(ns reacl-c.test-util.dom-testing-test
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom]
            [reacl-c.base :as base]
            [reacl-c.test-util.dom-testing :as dom-t :include-macros true]
            [clojure.string :as string]
            ["react-dom/test-utils" :as rt]
            [cljs-async.core :refer (async await) :include-macros true]
            [cljs-async.test :refer (deftest) :include-macros true]
            [cljs.test :refer (is testing) :include-macros true]))

(deftest get-query-test-1
  (dom-t/rendering
    (dom/div (dom/button {:title "Hello"}))
    (fn [env]
      (is (some? (dom-t/get env (dom-t/by-title "Hello")))))))

(deftest double-test
  (dom-t/rendering
    (dom/div (dom/button {:title "Hello"}))
    (fn [env1]
      (dom-t/rendering
       (dom/div "Foo")
       (fn [env2]
         (is (some? (dom-t/get env2 (dom-t/by-text "Foo"))))
         (is (some? (dom-t/get env1 (dom-t/by-title "Hello")))))))))

(deftest fire-event-test-2
  (dom-t/rendering
    (c/with-state-as x
      (dom/button {:title (if x "World" "Hello")
                   :type "button"
                   :onclick (constantly true)}))
    :state false
    (fn [env]
      (async
       (let [node (await (dom-t/find env (dom-t/by-title "Hello")))]
         (is (some? node))
         (dom-t/fire-event node :click)
         (await (dom-t/find env (dom-t/by-title "World"))))))))

(deftest build-query-test
  (let [by-color (dom-t/build-query
                  (fn [where col]
                    ;; Note: one could use other queries on 'where' as a base, resp. dive down into the whole tree of nodes from (.-container where)
                    (filter #(= col (.-color (.-style %)))
                            (.-childNodes (.-container where))))
                  (fn [where col]
                    (str "More than one node with color: " col))
                  (fn [where col]
                    (str "No node with color: " col)))]
    (dom-t/rendering
     (dom/div {:style {:color "black"}})
     :state false
     (fn [env]
       (is (some? (dom-t/query env (by-color "black"))))
       (is (nil? (dom-t/query env (by-color "white"))))

       (is (not-empty (dom-t/query-all env (by-color "black"))))

       (is (string/starts-with? (try (dom-t/get env (by-color "pink"))
                                     (catch :default e
                                       (.-message e)))
                                (str "No node with color: " "pink")))))))
