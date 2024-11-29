(ns aiko-services-clj.process
  (:import [java.lang System]
           [java.lang.management ProcessHandle]
           [java.net InetAddress])
  (:require [aiko-services-clj.utilities.config :as config]
            [aiko-services-clj.utilities.connection :as connection]
            [aiko-services-clj.transport.mqtt :as MQTT :refer [MQTT Castaway]]
            [clojure.tools.logging :as logging :refer [warn error]]))





(defn get-namespace []
  (or (System/getenv "AIKO_NAMESPACE"  config/_AIKO_NAMESPACE)
      (:AIKO_NAMESPACE config/defaults)))

(defn get-hostname []
  (or (.getCanonicalHostName (InetAddress/getLocalHost))
      (:AIKO_MQTT_HOST config/defaults)))

(defn get-process-id []
  (.pid (ProcessHandle/current)))



(defprotocol IProcess
  (get-topic-path [cls service-id])
  (get-topic-process-path [this])
  (get-logger [this])
  (get-connection [this])
  (get-message [this])
  (get-process [this])
  (get-registrar [this])
  (get-topic-in [this])
  (get-topic-out [this])
  (get-topic-log [this])
  (get-topic-lwt [this])
  (get-payload-lwt [this]))


(def TOPIC_REGISTRAR_BOOT (str (get-namespace) "/service/registrar"))

(defrecord ProcessData [connection logger message process registrar topic_process_path topic_path topic_in topic_out topic_log topic_lwt payload_lwt]
  IProcess
  (get-topic-path [this service-id]
    (str topic_process_path "/" service-id))
  (get-topic-process-path [this]
    topic_process_path)
  (get-logger [this]
    logger)
  (get-connection [this]
    connection)
  (get-message [this]
    message)
  (get-process [this]
    process)
  (get-registrar [this]
    registrar)
  (get-topic-path [this]
    topic_path)
  (get-topic-in [this]
    topic_in)
  (get-topic-out [this]
    topic_out)
  (get-topic-log [this]
    topic_log)
  (get-topic-lwt [this]
    topic_lwt)
  (get-payload-lwt [this]
    payload_lwt))

;; TODO: implement the connection --> connection.cljc
(defn create-connection []
  (connection/create-connection))

(defn create-process-data []
  (let [process-data (ProcessData.)
        topic-process-path (str (get-namespace) "/" (get-hostname) "/" (get-process-id))
        topic-path (str topic-process-path "/0")]
    (-> process-data
        (assoc :connection (create-connection))
        (assoc :logger nil)
        (assoc :message nil)
        (assoc :process nil)
        (assoc :registrar nil)
        (assoc :topic_process_path topic-process-path)
        (assoc :topic_path topic-path)
        (assoc :topic_in (str topic-path "/in"))
        (assoc :topic_out (str topic-path "/out"))
        (assoc :topic_log (str topic-path "/log"))
        (assoc :topic_lwt (str topic-path "/state"))
        (assoc :payload_lwt "(absent)"))))

;; make aiko a singleton
(def aiko (atom (create-process-data)))

;; get the aiko singleton
(defn get-aiko []
  @aiko)

;; set the aiko singleton
(defn set-aiko [aiko-data]
  (reset! aiko aiko-data))

(defprotocol IProcessImplementation
  (initialize [this mqtt-connection-required])
  (run [this loop-when-no-handlers mqtt-connection-required])
  (add-message-handler [this message-handler topic binary])
  (remove-message-handler [this message-handler topic])
  (add-service-to-registrar* [this service])
  (remove-service-from-registrar* [this service])
  (add-service [this service])
  (remove-service [this service])
  (on-message [this mqtt-client userdata message])
  (on-message-queue-handler [this message na])
  (on-registrar [this na1 topic payload-in])
  (set-last-will-and-testament [this topic-lwt payload-lwt retain-lwt])
  (terminate [this exit-status])
  (topic-matcher [this topic topics]))


(defprotocol IProcessServiceRecord
  (get-id [this])
  (get-service [this])
  (set-service [this service])
  (set-id [this id]))

(defrecord ProcessServiceRecord [id service]
  IProcessServiceRecord
  (get-id [this]
    id)
  (get-service [this]
    service)
  (set-service [this service]
    (assoc this :service service))
  (set-id [this id]
    (assoc this :id id)))

;; TODO: work out if we can reduce this list of fields
(defrecord ProcessImplementation [process-data connection
                                  logger message
                                  process registrar
                                  topic_process_path topic_path topic_in topic_out
                                  topic_log topic_lwt payload_lwt
                                  initialized running service-count exit-status
                                  message-handlers message-handlers-binary-topics
                                  message-handlers-wildcard-topics
                                  registrar-absent-terminate services]
  IProcess
  (get-topic-path [this service-id]
    (get-topic-path process-data service-id))
  (get-topic-process-path [this]
    (get-topic-process-path process-data))
  (get-logger [this]
    (get-logger process-data))
  (get-connection [this]
    (get-connection process-data))
  (get-message [this]
    (get-message process-data))
  (get-process [this]
    (get-process process-data))
  (get-registrar [this]
    (get-registrar process-data))
  (get-topic-in [this]
    (get-topic-in process-data))
  (get-topic-out [this]
    (get-topic-out process-data))
  (get-topic-log [this]
    (get-topic-log process-data))
  (get-topic-lwt [this]
    (get-topic-lwt process-data))
  (get-payload-lwt [this]
    (get-payload-lwt process-data))

  IProcessImplementation
  (initialize [this mqtt-connection-required]
    (if (not initialized)
      (do
        (assoc this
               :initialized true
               :running false
               :service-count 0
               :exit-status 0
               :message-handlers {}
               :message-handlers-binary-topics {}
               :message-handlers-wildcard-topics []
               :registrar-absent-terminate false
               :services (atom {:service-count 0
                                :services []})
               :mqtt-connected false)
        (.add_queue_handler event on-message-queue-handler ["message"])
        (add-message-handler this on-registrar TOPIC_REGISTRAR_BOOT false)
        (set-message. aiko (Castaway.))
        (try
          (set-message aiko (MQTT. on-message
                                   (:message-handlers this)
                                   (get-topic-lwt aiko)
                                   (get-payload-lwt aiko)
                                   false))
          (assoc this :mqtt-connected true)
          (catch SystemError system-error
            (if mqtt-connection-required
             (error system-error)
              (warn system-error))))
        (when (and mqtt-connection-required
                   (not (:mqtt-connected this))) 
          (error "MQTT connection required but not connected")
          (terminate this 1))

        (let [mqtt-connection (if mqtt-connection-required
                                (get-connection process-data)
                                nil)]
          (assoc this :initialized true
                 :running false
                 :service-count 0
                 :exit-status 0
                 :message-handlers {}
                 :message-handlers-binary-topics {}
                 :message-handlers-wildcard-topics {}
                 :registrar-absent-terminate false
                 :services {}
                 :services-lock (Object.)))))))