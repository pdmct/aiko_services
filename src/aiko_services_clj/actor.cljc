(ns aiko-services-clj.actor
  (:require [clojure.tools.logging :as log]
            [aiko-services-clj.service :as service :refer [IService]])
  (:import [java.lang Exception Class]))

(def actor-topic {:in "in"
                  :out "out"
                  :state "state"
                  :control "control"})

(defprotocol IMessage
  (invoke [this]))


(defprotocol IActor
  (run [this mqtt-connection-required]))
=

(defrecord Message [target-object command arguments target-function]
  IMessage
  (invoke [this]
    (let [resolved-function
          (or target-function
              (try
                (some-> target-object
                        (.getClass)
                        (.getMethod command (into-array Class (map class arguments)))
                        (.invoke target-object))
                (catch Exception e
                  nil)))]
      (if resolved-function
        (try
          (apply resolved-function arguments)
          (catch Exception e
            (log/error "Error invoking function:" (.getMessage e))
            (throw e)))
        (do
          (log/error "No target function found for command:" command)
          (println "No target function found"))))))


(deftype Actor [serv-impl context]
  IService
  (add-message-handler [this message-handler topic binary]
    (.add-message-handler serv-impl message-handler topic binary))
  (remove-message-handler [this message-handler topic]
    (.remove-message-handler serv-impl message-handler topic))
  (add-registrar-handler [this registrar-handler]
    (.add-registrar-handler serv-impl registrar-handler))
  (run [this]
    (.run serv-impl))
  (stop [this]
    (.stop serv-impl))
  (add-tags [this tags]
    (.add-tags serv-impl tags))
  (add-tags-string [this tags-string]
    (.add-tags-string serv-impl tags-string))
  (get-tags-string [this]
    (.get-tags-string serv-impl))
  (set-registrar-handler [this registrar-handler]
    (.set-registrar-handler serv-impl registrar-handler))
  
  IActor
  (run [this mqtt-connection-required]
    (println "Actor running")))


(defn proxy-post-message
  "Post a message to an actor"
  [actor proxy-name actual-object actual-function & args]
  (let [command (:name actual-function)
        control-command (.startsWith command (:control actor-topic))
        topic (if control-command
                (:control actor-topic)
                (:in actor-topic))]
    (.post-message actual-object
                   topic
                   command
                   args
                   {:target-function (:func actual-function)})
    (println "Posting message to actor" actor)))

(defn create-actor
  "Create an actor"
  [context logger share ec-producer delayed-message-queue ]
  (let [service (service/service)
        actor (Actor. service context)]
  (new Actor service context)))

