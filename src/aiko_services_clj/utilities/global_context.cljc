(ns aiko-services-clj.utilities.global-context)

;; this is in a utilty class but included here for now
(defprotocol IGlobalContext
  (get-aiko [this])
  (get-message [this])
  (set-aiko [this aiko])
    (set-message [this message]))

(defrecord GlobalContext [aiko message]
  IGlobalContext
  (get-aiko [this]
    aiko)
  (get-message [this]
    message)
  (set-aiko [this aiko]
    (GlobalContext. aiko message))
  (set-message [this message]
    (GlobalContext. aiko message)))

(def global-context (atom (GlobalContext. nil nil)))

(defn get-global-context []
  @global-context)

(defn set-global-context [context]
  (reset! global-context context))