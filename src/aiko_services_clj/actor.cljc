(ns aiko-services-clj.actor
  (:require [clojure.tools.logging :as log]))

(def actor-topic {:in "in"
                  :out "out"
                  :state "state"
                  :control "control"})

(defprotocol IMessage
  (invoke [this]))

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
           (println "No target function found")))))))

(defprotocol IActor 
  (run [this])
  (run [this mqtt-connection-required]))

(deftype Actor [context]
  IActor 
  (run [this]
        (println "Actor running"))
  (run [this mqtt-connection-required]
    (println "Actor running")))