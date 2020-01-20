(ns reacld.base)

(defprotocol E)

(defrecord WithState [f args] E)
(defrecord Focus [e lens] E)
(defrecord HandleAction [e f args] E)
(defrecord MapAction [e f args] E)
(defrecord LocalState [e initial] E)
;; TODO: rename did-mount, will-unmount and did-update ?
(defrecord WhenMounted [e f args] E)
(defrecord WhenUnmounting [e f args] E)
(defrecord AfterUpdate [e f args] E)
(defrecord WithAsyncActions [f args] E)
(defrecord MonitorState [e f args] E)

(defrecord Dom [type attrs events children] E)
(defrecord Keyed [e key] E)
(defrecord DomFragment [children] E)

(defprotocol Effect
  (-run-effect! [this]))

(defrecord PassAction [])
(defrecord MultiAction [actions])
