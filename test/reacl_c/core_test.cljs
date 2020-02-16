(ns reacl-c.test.core-test
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.base :as base]
            [reacl-c.dom :as dom]
            [reacl-c.test-util.core :as tu]
            [reacl-c.test-util.xpath :as xpath :include-macros true]
            [reacl-c.browser :as browser]
            [active.clojure.lens :as lens]
            [cljs.test :refer (is deftest testing) :include-macros true]))

(deftest item-equality-test
  ;; all item should be referentially equal
  (testing "div"
    (is (= (dom/div) (dom/div)))
    (is (= (dom/div "a") (dom/div "a")))
    (is (= (dom/div {:onclick identity}) (dom/div {:onclick identity})))
    (is (= (dom/div (dom/div "a")) (dom/div (dom/div "a")))))
  (testing "dynamic"
    (let [f (fn [x] (dom/div x))]
      (is (= (c/dynamic f) (c/dynamic f)))))
  (testing "focus"
    (is (= (c/focus :a (dom/div)) (c/focus :a (dom/div)))))
  (testing "handle-action"
    (let [f (fn [state a])]
      (is (= (c/handle-action (dom/div) f) (c/handle-action (dom/div) f)))))
  (testing "add-state"
    (is (= (c/add-state :a :b (dom/div)) (c/add-state :a :b (dom/div)))))
  (testing "keyed"
    (is (= (c/keyed (dom/div) :a) (c/keyed (dom/div) :a))))
  (testing "once"
    (is (= (c/once (c/constantly (c/return :action :a)) (c/constantly (c/return :action :b))) (c/once (c/constantly (c/return :action :a)) (c/constantly (c/return :action :b))))))
  (testing "with-async-actions"
    (is (= (c/with-async-actions :f :a) (c/with-async-actions :f :a))))
  (testing "monitor-state"
    (is (= (c/handle-state-change (dom/div) :f) (c/handle-state-change (dom/div) :f))))
  )

(deftest merge-lens-test
  ;; independant fields
  (is (= (lens/yank [{:a 42} {:b 13}] c/merge-lens)
         {:a 42 :b 13}))
  (is (= (lens/shove [{:a 42} {:b 13}] c/merge-lens {:a 110 :b 130})
         [{:a 110} {:b 130}]))

  ;; shadow outer
  (is (= (lens/yank [{:a 42} {:a 11 :b 13}] c/merge-lens)
         {:a 11 :b 13}))
  (is (= (lens/shove [{:a 42} {:a 11 :b 13}] c/merge-lens {:a 110 :b 130})
         [{:a 42} {:a 110 :b 130}]))

  ;; new fields go into outer
  (is (= (lens/shove [{} {:a 11}] c/merge-lens {:a 110 :b 130})
         [{:b 130} {:a 110}]))

  ;; missing values become nil (= inner keys are stable; would be hard to follow lens laws otherwise)
  (is (= (lens/shove [{} {:a 11 :b 17}] c/merge-lens {:b 110})
         [{} {:a nil :b 110}])))

(deftest subscription-test
  (let [subscribed (atom false)
        sub-impl (fn [deliver! x]
                   (reset! subscribed true)
                   (fn []
                     (reset! subscribed false)))
        sub (c/subscription sub-impl :x)
        env (tu/env (c/dynamic #(if % sub "")))]
    (tu/mount! env false)
    
    ;; sub on mount
    (let [r (tu/update! env true)
          a (first (:actions r))]
      (is (some? a))
      (is (tu/subscribe-effect? a sub))

      (is (not @subscribed))

      ;; execute subscribe effect.
      (is (= (c/return)
             (tu/execute-effect! env a)))

      (is @subscribed))

    ;; unsub on unmount
    (let [r (tu/update! env false)
          a (first (:actions r))]
      (is (tu/unsubscribe-effect? a sub))

      (is (= (c/return)
             (tu/execute-effect! env a)))
      (is (not @subscribed)))))

(deftest defn-named-test
  (testing "schematized args and state"
    (c/defn-named ^:always-validate defn-named-test-1 [a :- schema.core/Str]
      (dom/div (str a)))
    (is (base/item? (defn-named-test-1 "foo")))

    (try (defn-named-test-1 42)
         (is false)
         (catch :default e
           (is true))))

  (testing "it is named"
    (c/defn-named defn-named-test-2 "mydoc" [a]
      (dom/div (str a)))
    (is (contains? (meta defn-named-test-2) :reacl-c.core/name-id))

    ;; no clue why this test fails: (is (= '([a]) (:arglists (meta #'defn-named-test-2))))
    (is (= "mydoc" (:doc (meta #'defn-named-test-2)))))

  (testing "checks arity"
    (c/defn-named defn-named-test-3 [a]
      (dom/div (str a)))
    (try (defn-named-test-3)
         (is false)
         (catch :default e
           (is true)))))

(deftest defn-dynamic-test
  (testing "schematized args and state"
    (c/defn-dynamic ^:always-validate defn-dynamic-test-1 state :- schema.core/Int [a :- schema.core/Str]
      (dom/div (str state a)))
    (is (base/item? (defn-dynamic-test-1 "foo")))

    (do (tu/mount! (tu/env (defn-dynamic-test-1 "abc")) 42)
        (is true))

    ;; throws on data not matching schema:
    (tu/preventing-error-log
     (fn []
       (try (defn-dynamic-test-1 42)
         
            (is false)
            (catch :default e
              (is true)))
       (try (tu/mount! (tu/env (defn-dynamic-test-1 "abc")) "42")
            (is false)
            (catch :default e
              (is true))))))

  (testing "it is named"
    (c/defn-dynamic defn-dynamic-test-2 "mydoc" state [a]
      (dom/div (str state a)))
    (is (contains? (meta defn-dynamic-test-2) :reacl-c.core/name-id))
    ;; no clue why this test fails: (is (= '([a]) (:arglists (meta #'defn-dynamic-test-2))))
    (is (= "mydoc" (:doc (meta #'defn-dynamic-test-2))))
    )

  (testing "checks arity on call"
    (c/defn-dynamic defn-dynamic-test-3 state [a]
      (dom/div (str state a)))
    (try (defn-dynamic-test-3)
         (is false)
         (catch :default e
           (is true))))
  
  (testing "a regression with varargs"
    (c/defn-dynamic defn-dynamic-test-4 state [& args]
      (dom/div))

    (is (= (defn-dynamic-test-4) (defn-dynamic-test-4)))

    (is (= (defn-dynamic-test-4 "x") (defn-dynamic-test-4 "x")))

    (c/defn-dynamic defn-dynamic-test-5 "docstring" state [a1 & args]
      (dom/div))

    (is (= "docstring")
        (:doc (meta #'defn-dynamic-test-5)))

    (is (= (defn-dynamic-test-5 "x") (defn-dynamic-test-5 "x")))

    (is (= (defn-dynamic-test-5 "x" "y") (defn-dynamic-test-5 "x" "y")))))

(deftest with-async-messages-test
  (let [env (tu/env (c/with-ref (fn [ref]
                                  (c/with-async-messages
                                    (fn [send!]
                                      (dom/div (-> (c/handle-message (fn [state msg]
                                                                       (c/return :state msg))
                                                                     (dom/div))
                                                   (c/set-ref ref))
                                               (-> (c/once (c/constantly (c/return :action ::test)))
                                                   (c/handle-action (fn [_ _]
                                                                      (send! ref :msg)
                                                                      (c/return))))))))))]
    (is (= (c/return :state :msg)
           (tu/mount! env :st)))))

(deftest sync-messages-test
  (let [env (tu/env (c/with-ref (fn [ref]
                                  (c/with-async-messages
                                    (fn [send!]
                                      (dom/div (-> (c/handle-message (fn [state msg]
                                                                       (c/return :state msg))
                                                                     (dom/div))
                                                   (c/set-ref ref))
                                               (c/once (c/constantly (c/return :message [ref :msg])))))))))]
    (is (= (c/return :state :msg)
           (tu/mount! env :st)))))

(deftest app-send-message-test
  (let [e (js/document.createElement "div")
        received (atom nil)
        app (browser/run e
              (c/handle-message (fn [state msg]
                                  (reset! received msg)
                                  (c/return))
                                c/empty)
              nil)]
    (c/send-message! app ::hello)
    (is (= ::hello @received))))

(deftest map-messages-test
  (let [env (tu/env (c/map-messages (fn [msg] [:x msg])
                                    (c/handle-message (fn [state msg]
                                                        (c/return :state msg))
                                                      (dom/div))))]
    (tu/mount! env :st)
    (is (= (c/return :state [:x :msg])
           (tu/send-message! (tu/get-component env) :msg)))))

(deftest redirect-messages-test
  (let [env (tu/env (c/with-ref
                      (fn [ref]
                        (c/fragment
                         (c/redirect-messages ref
                                              (dom/div (dom/div)
                                                       (-> (c/handle-message (fn [state msg]
                                                                               (c/return :state msg))
                                                                             (dom/div))
                                                           (c/set-ref ref))))))))]
    (tu/mount! env :st)
    (is (= (c/return :state :msg)
           (tu/send-message! (tu/get-component env) :msg))))
  )

;; TODO: test every higher level feature in core.
