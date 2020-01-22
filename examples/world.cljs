(ns ^:no-doc examples.world
  (:require [reacld.core :as r :include-macros true]
            [reacld.browser :as browser]
            [reacld.dom :as dom]))

(r/defn-subscription interval-timer deliver! [ms]
  {:pre [(integer? ms)]}
  (println "starting timer!")
  (let [id (.setInterval js/window (fn [] (deliver! (js/Date.))) ms)]
    (fn stop []
      (println "stopping timer!")
      (.clearInterval js/window id))))

(r/def-dynamic show-date date
  (.toLocaleTimeString date))

(def clock
  (r/isolate-state (js/Date.)
                   (dom/div (-> (interval-timer 1000)
                                (r/handle-action (fn [state date] (r/return :state date))))
                            show-date)))

(defrecord Effect [f args])

(defn reload [force?]
  (r/return :action (Effect. #(.reload (.-location js/window) force?) nil)))

(defn effects [_ {f :f args :args}]
  (apply f args)
  (r/return))

(defrecord Show [_])
(defrecord Hide [_])

(defn show-hide [_ a]
  (cond
    (instance? Show a) (r/return :state true)
    (instance? Hide a) (r/return :state false)
    :else a))

(r/def-dynamic world-app show?
  (-> (if show?
        (dom/div (dom/button {:onclick ->Hide} "Hide")
                 clock
                 (dom/button {:onclick (constantly (reload true))} "Reload"))
        (dom/button {:onclick ->Show} "Show"))
      (r/handle-action show-hide)
      (r/handle-action effects)))

(browser/run (.getElementById js/document "app-world")
  world-app
  true)
