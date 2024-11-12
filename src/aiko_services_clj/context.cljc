(ns aiko-services-clj.context
  (:require [clojure.string :as str])
  (:import [java.lang Exception]))


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


;; Context functions & defaults
(def DEFAULT_PARAMETERS {})
(def DEFAULT_PROTOCOL "*")
(def DEFAULT_TAGS [])
(def DEFAULT_TRANSPORT "mqtt")
(def DEFAULT_DEFINITION "")
(def DEFAULT_DEFINITION_PATHNAME "")
(def DEFAULT_STREAM_ID 0)
(def DEFAULT_FRAME_ID 0)

(defprotocol IContext
  (get-implementation [this])
  (get-implementations [this])
  (get-name [this])
  (set-implementation [this impl-name impl])
  (set-implementations [this impls]))


(deftype Context [name implementations]
  IContext
  (get-implementation [this]
    (get-in implementations [name]))
  (get-implementations [this]
    implementations)
  (get-name [this]
    name)
  (set-implementation [this impl-name impl]
    (assoc this :implementations (assoc implementations impl-name impl)))
  (set-implementations [this impls]
    (assoc this :implementations impls)))

(defprotocol IContextService
  (get-parameters [this])
  (get-protocol [this])
  (get-tags [this])
  (get-transport [this])
  (set-protocol [this protocol]))


;; create ContextService type by composing Context with IContextService
(deftype ContextService [context parameters protocol tags transport]
  IContext
  (get-implementation [this]
    (get-implementation context))
  (get-implementations [this]
    (get-implementations context))
  (get-name [this]
    (get-name context))
  (set-implementation [this impl-name impl]
    (set-implementation context impl-name impl))
  (set-implementations [this impls]
    (set-implementations context impls))

  IContextService
  (get-parameters [this]
    parameters)
  (get-protocol [this]
    protocol)
  (get-tags [this]
    tags)
  (get-transport [this]
    transport)
  (set-protocol [this protocol]
    (assoc this :protocol protocol)))


;; validation functions for context
(defn valid-name [context-service]
  (cond
    (or (nil? (get-name context-service))
        (not (string? (get-name context-service))))
    (throw (Exception. "ContextService name is required and must be a string"))
    (empty? (get-name context-service))
    (throw (Exception. "ContextService name cannot be empty string"))
    :else context-service))

(defn valid-parameters [context-service]
  (if (nil? (get-parameters context-service))
    (ContextService. (.context context-service) DEFAULT_PARAMETERS (get-protocol context-service) (get-tags context-service) (get-transport context-service))
    context-service))

(defn valid-protocol [context-service]
  (if (nil? (get-protocol context-service))
    (ContextService. (.context context-service) (get-parameters context-service) DEFAULT_PROTOCOL (get-tags context-service) (get-transport context-service))
    context-service))

(defn valid-tags [context-service]
  (if (nil? (get-tags context-service))
    (ContextService. (.context context-service) (get-parameters context-service) (get-protocol context-service) DEFAULT_TAGS (get-transport context-service))
    context-service))

(defn valid-transport [context-service]
  (if (nil? (get-transport context-service))
    (ContextService. (.context context-service) (get-parameters context-service) (get-protocol context-service) (get-tags context-service) DEFAULT_TRANSPORT)
    context-service))

(defn validate-context-service
  [context-service]
  (-> context-service
      valid-name
      valid-parameters
      valid-protocol
      valid-tags
      valid-transport))


(defn create-context-service
  ([] (ContextService. (Context. "<interface>" {}) DEFAULT_PARAMETERS DEFAULT_PROTOCOL DEFAULT_TAGS DEFAULT_TRANSPORT))
  ([context] (validate-context-service
              (ContextService. context DEFAULT_PARAMETERS DEFAULT_PROTOCOL DEFAULT_TAGS DEFAULT_TRANSPORT)))
  ([context parameters] (validate-context-service
                         (ContextService. context parameters DEFAULT_PROTOCOL DEFAULT_TAGS DEFAULT_TRANSPORT)))
  ([context parameters protocol] (validate-context-service
                                  (ContextService. context parameters protocol DEFAULT_TAGS DEFAULT_TRANSPORT)))
  ([context parameters protocol tags] (validate-context-service
                                       (ContextService. context parameters protocol tags DEFAULT_TRANSPORT)))
  ([context parameters protocol tags transport] (validate-context-service
                                                 (ContextService. context parameters protocol tags transport))))
