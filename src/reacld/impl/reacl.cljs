(ns reacld.impl.reacl
  (:require [reacld.base :as base]
            [reacld.dom :as dom]
            [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as rdom]
            [clojure.string :as str]))

(defprotocol ^:private IReacl
  (-instantiate-reacl [this binding]))

(defrecord ^:private LiftedClass [class args]
  base/E
  IReacl
  (-instantiate-reacl [this binding]
    (if (and (reacl/reacl-class? class) (reacl/has-app-state? class))
      (apply class binding args)
      (apply class args))))

(defn lift [class & args]
  (apply LiftedClass. class args))

(defn instantiate
  "Returns a Reacl component/element for the given element and state binding."
  [binding e]
  (cond
    (satisfies? IReacl e) (-instantiate-reacl e binding)
    ;; or strings, usually
    :else (rdom/fragment e)))

(defn- instantiate-child [binding e]
  ;; TODO: return multiple elements, removing fragments?
  (cond
    (satisfies? IReacl e) (-instantiate-reacl e binding)
    ;; or strings, usually
    :else e))

(defrecord ^:private ActionMessage [action])

(defn- pass-message [child msg]
  (reacl/return :message [(reacl/get-dom child) msg]))

(reacl/defclass ^:private toplevel this state [e]
  refs [child]
  
  render
  (-> (instantiate (reacl/bind this) e)
      (reacl/refer child)
      (reacl/action-to-message this ->ActionMessage))

  handle-message
  (fn [msg]
    (cond
      (instance? ActionMessage msg)
      (let [action (:action msg)]
        ;; TODO: throw or warn only?
        (throw (ex-info "Unhandled toplevel action." {:value action}))) 

      :else (pass-message child msg))))

(defrecord ^:private ReaclApplication [comp]
  base/Application  
  (-send-message! [this msg]
    (reacl/send-message! comp msg)))

(defn run
  "Run and mount the given element `e` in the native dom element
  `dom`, with the given initial state."
  [dom e initial-state]
  (ReaclApplication. (reacl/render-component dom
                                             toplevel
                                             initial-state
                                             e)))

(defn- transform-return [r child]
  ;; a base/Returned value to a reacl/return value.
  (assert (instance? base/Returned r) (str "Expected a value created by 'return', but got: " (pr-str r) "."))
  (apply reacl/merge-returned
         (if-let [st (:opt-state r)]
           (reacl/return :app-state (first st))
           (reacl/return))
         (concat (map (fn [a] (reacl/return :action a))
                      (:actions r))
                 (map (fn [a] (reacl/return :message [child a]))
                      (:messages r)))))


(reacl/defclass ^:private handle-message this state [e f & args]
  refs [child]

  handle-message
  (fn [msg]
    (transform-return (apply f state msg args)
                      (reacl/get-dom child)))

  render
  (-> (instantiate (reacl/bind this) e)
      (reacl/refer child)))

(extend-type base/HandleMessage
  IReacl
  (-instantiate-reacl [{e :e f :f args :args} binding]
    (apply handle-message binding e f args)))

(defn- gen-named [s]
  (reacl/class s this state [e]
               refs [child]
               handle-message (fn [msg] (pass-message child msg))
               render (-> (instantiate (reacl/bind this) e)
                          (reacl/refer child))))

(def ^:private named (memoize gen-named))

(extend-type base/Named
  IReacl
  (-instantiate-reacl [{e :e name :name} binding]
    ((named name) binding e)))

(defrecord ^:private EventMessage [ev])

(defn- find-event-handler [ev events]
  (let [en (str/lower-case (.-type ev))]
    ;; OPT: could be done a little faster - try a lookup, or prepare a different map. But it can be 'onchange' and 'onChange'.
    (some (fn [[n f]]
            ;; "change" = :onChange ?
            ;; FIXME: are things like onChangeCapture possible? how to handle them?
            (and (= en (str/lower-case (subs (name n) 2)))
                 f))
          events)))

(defn- dom-message-to-action [events]
  (fn [msg]
    (cond
      (instance? EventMessage msg)
      (let [{ev :ev} msg
            f (find-event-handler ev events)]
        (if-let [a (f ev)]
          (reacl/return :action a)
          (reacl/return)))

      :else
      ;; TODO else messages to (only named/refered?) children?
      (throw (ex-info "Sending messages to a dom element not implemented yet." {:value msg})))))

(defn- dom-event-handler [target]
  (fn [ev]
    (reacl/send-message! target (EventMessage. ev))))

(defn- merge-dom-attrs [target attrs events handler]
  (let [r-events (into {} (map (fn [[k v]]
                                 [k handler])
                               events))]
    (merge attrs r-events)))

(reacl/defclass ^:private dom++ this state [type attrs events children]
  local-state [handler (dom-event-handler this)]
  handle-message (dom-message-to-action events)
  
  render
  (apply rdom/element type (merge-dom-attrs this attrs events handler)
         (map (partial instantiate (reacl/bind this)) children)))

(reacl/defclass ^:private dom+ this [type attrs events]
  local-state [handler (dom-event-handler this)]
  handle-message (dom-message-to-action events)
  
  render
  (rdom/element type (merge-dom-attrs this attrs events handler)))

(defn dom- [binding type attrs children]
  (apply rdom/element type attrs (map (partial instantiate-child binding) children)))

(defn- dom [binding type attrs events children]
  ;; optimize for dom element without event handlers:
  (if (not-empty events)
    (if (empty? children)
      (dom+ type attrs events)
      ;; OPT: if no child needs state, then we can use a non-stateful class here too
      (dom++ binding type attrs events children))
    (dom- binding type attrs children)))

(extend-type dom/Element
  IReacl
  (-instantiate-reacl [{type :type attrs :attrs events :events children :children} binding]
    (dom binding type attrs events children)))

(reacl/defclass ^:private fragment this state [children] ;; TODO: remove these from runtime (resolve earlier?)
  handle-message
  (fn [msg]
    (throw (ex-info "Sending messages to a fragment element not implemented yet." {:value msg})))
  
  render (apply rdom/fragment (map (partial instantiate (reacl/bind this))
                                   children)))

(extend-type base/Fragment
  IReacl
  (-instantiate-reacl [{children :children} binding]
    (fragment binding children)))

(defn- keyed [binding e key]
  (-> (instantiate binding e)
      (reacl/keyed key)))

(extend-type base/Keyed
  IReacl
  (-instantiate-reacl [{e :e key :key} binding]
    (keyed binding e key)))

(reacl/defclass ^:private with-state this state [f & args]
  refs [child]
  
  handle-message
  (fn [msg]
    (pass-message child msg))
  
  render
  (-> (instantiate (reacl/bind this) (apply f state args))
      (reacl/refer child)))

(extend-type base/WithState
  IReacl
  (-instantiate-reacl [{f :f args :args} binding]
    (apply with-state binding f args)))

(defn- focus [binding e lens]
  (instantiate (reacl/focus binding lens) e))

(extend-type base/Focus
  IReacl
  (-instantiate-reacl [{e :e lens :lens} binding]
    (focus binding e lens)))

(reacl/defclass ^:private handle-action this state [e f & args]
  local-state [action-to-message
               (fn [_ action]
                 (reacl/return :message [this (ActionMessage. action)]))]

  refs [child]
  
  handle-message
  (fn [msg]
    (cond
      (instance? ActionMessage msg)
      (do
        ;; makes up a nice trace: (println "AM:" (:action msg) "=>" (apply f state (:action msg) args))
        (transform-return (apply f state (:action msg) args)
                          (reacl/get-dom child)))
      :else
      (pass-message child msg)))
  
  render
  (-> (instantiate (reacl/bind this) e)
      (reacl/refer child)
      (reacl/reduce-action action-to-message)))

(extend-type base/HandleAction
  IReacl
  (-instantiate-reacl [{e :e f :f args :args} binding]
    (apply handle-action binding e f args)))

(defrecord ^:private NewIsoState [state])

(defn- id-state [st1 st2]
  ;; TODO: use = instead? or allow the user to specify it?
  (if (identical? st1 st2)
    reacl/keep-state
    st2))

(reacl/defclass ^:private local-state this astate [e initial]
  local-state [lstate initial]

  refs [child]
  
  render
  (-> (instantiate (reacl/use-reaction [astate lstate]
                                       (reacl/reaction this ->NewIsoState))
                   e)
      (reacl/refer child))
  
  handle-message
  (fn [msg]
    (cond
      (instance? NewIsoState msg)
      (let [{[new-app-state new-local-state] :state} msg]
        (reacl/return :app-state (id-state astate new-app-state)
                      :local-state (id-state lstate new-local-state)))
      :else
      (pass-message child msg))))

(extend-type base/LocalState
  IReacl
  (-instantiate-reacl [{e :e initial :initial} binding]
    (local-state binding e initial)))

(defrecord ^:private AsyncAction [v])

;; TODO: can't we make the element returned by f fixed? - like an 'inject actions?'
(reacl/defclass ^:private with-async-action this state [f & args]
  local-state [deliver! (fn [action]
                          (reacl/send-message! this (AsyncAction. action)))]

  refs [child]
  
  render
  (-> (instantiate (reacl/bind this) (apply f deliver! args))
      (reacl/refer child))

  handle-message
  (fn [msg]
    (cond
      (instance? AsyncAction msg)
      (reacl/return :action (:v msg))
      
      :else
      (pass-message child msg))))

(extend-type base/WithAsyncActions
  IReacl
  (-instantiate-reacl [{f :f args :args} binding]
    (apply with-async-action binding f args)))

(reacl/defclass ^:private when-mounted this state [e f & args]
  refs [child]

  handle-message
  (fn [msg]
    (pass-message child msg))
  
  component-did-mount
  (fn []
    ;; FIXME: it's only a dom node, if e is dom element - can we change that? (esp. fragments are a problem then?)
    (reacl/return :action (apply f (reacl/get-dom child) args)))

  render (-> (instantiate (reacl/bind this) e)
             (reacl/refer child)))

(extend-type base/WhenMounted
  IReacl
  (-instantiate-reacl [{e :e f :f args :args} binding]
    (apply when-mounted binding e f args)))

(reacl/defclass ^:private when-unmounting this state [e f & args]
  refs [child]

  handle-message
  (fn [msg]
    (pass-message child msg))

  component-will-unmount
  (fn []
    ;; FIXME: see mount.
    (reacl/return :action (apply f (reacl/get-dom child) args)))

  render (-> (instantiate (reacl/bind this) e)
             (reacl/refer child)))

(extend-type base/WhenUnmounting
  IReacl
  (-instantiate-reacl [{e :e f :f args :args} binding]
    (apply when-unmounting binding e f args)))

(reacl/defclass ^:private after-update this state [e f & args]
  refs [child]

  handle-message
  (fn [msg]
    (pass-message child msg))
  
  component-did-update
  (fn [prev-app-state prev-local-state prev-e prev-f & prev-args]
    ;; FIXME: it's only a dom node, if e is dom element - can we change that? (esp. fragments are a problem then?)
    ;; TODO: pass old/new state to f?
    (reacl/return :action (apply f (reacl/get-dom child) args)))

  render (-> (instantiate (reacl/bind this) e)
             (reacl/refer child)))

(extend-type base/AfterUpdate
  IReacl
  (-instantiate-reacl [{e :e f :f args :args} binding]
    (apply after-update binding e f args)))

(defrecord ^:private MonitorMessage [new-state])

;; TODO: monitor that state change desire, or use 'after-update' for this?
(reacl/defclass ^:private monitor-state this state [e f & args]
  refs [child]
  
  render
  (-> (instantiate (reacl/use-reaction state (reacl/reaction this ->MonitorMessage)) e)
      (reacl/refer child))

  handle-message
  (fn [msg]
    (cond
      (instance? MonitorMessage msg)
      (let [new-state (:new-state msg)]
        (reacl/merge-returned (if-let [a (apply f state new-state args)]
                                (reacl/return :action a)
                                (reacl/return))
                              (reacl/return :app-state new-state)))
      :else
      (pass-message child msg))))

(extend-type base/MonitorState
  IReacl
  (-instantiate-reacl [{e :e f :f args :args} binding]
    (apply monitor-state binding e f args)))

(defn- fst-lens
  ([[a _]] a)
  ([[_ b] a] [a b]))

(reacl/defclass ^:private error-boundary this state [e f & args]
  
  refs [child]
  
  handle-message (fn [msg] (pass-message child msg))
  
  render
  (-> (instantiate (reacl/bind this) e)
      (reacl/refer child))

  component-did-catch
  (fn [error info]
    ;; Note: info is already deprecated in React, in the sense that
    ;; 'getDerivedStateFromError' does not have it. It's also very
    ;; implementation dependant, and less informative in our setup.
    ;; Leave that our for now.
    (reacl/return :action (apply f error args))))

(extend-type base/ErrorBoundary
  IReacl
  (-instantiate-reacl [{e :e f :f args :args} binding]
    (apply error-boundary binding e f args)))
