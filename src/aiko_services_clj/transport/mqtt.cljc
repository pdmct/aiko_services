(ns aiko-services-clj.transport.mqtt 
  (:require
    [aiko-services-clj.transport.mqtt :as MQTT]))

;; MQTT transport info for managing a list of available mqtt hosts
(def mqtt-hosts (atom #{}))

(defprotocol ITransport
  (connect [this])
  (disconnect [this])
  (subscribe [this topic])
  (unsubscribe [this topic])
  (publish [this topic message]))


(deftype mqtt [host port transport]
  ITransport
  (connect [this]
    (println "MQTT connect"))
  (disconnect [this]
    (println "MQTT disconnect"))
  (subscribe [this topic]
    (println "MQTT subscribe"))
  (unsubscribe [this topic]
    (println "MQTT unsubscribe"))
  (publish [this topic message]
    (println "MQTT publish")))

(deftype Castaway []
  ITransport
  (connect [this]
    (println "Castaway connect"))
  (disconnect [this]
    (println "Castaway disconnect"))
  (subscribe [this topic]
    (println "Castaway subscribe"))
  (unsubscribe [this topic]
    (println "Castaway unsubscribe"))
  (publish [this topic message]
    (println "Castaway publish")))


(defn create-transport [protocol transport host port]
  (case protocol
    "mqtt" (mqtt. host port transport)
    "castaway" (Castaway.)))

(defn MQTT []
  (create-transport "mqtt" "tcp" "localhost" 1883))
