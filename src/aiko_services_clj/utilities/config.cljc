(ns aiko-services-clj.utilities.config
  (:require [nomad.config :as n]
            [clojure.core :as core :refer [slurp get-in with-open]]
            [clojure.java.io :as io]
            [aiko-services-clj.transport.mqtt :as mqtt :refer [mqtt-hosts]])
  (:import [java.net Socket InetAddress]
           [java.lang System Exception ProcessHandle]))

(def defaults {:AIKO_MQTT_HOST "localhost"
               :AIKO_MQTT_PORT 1883
               :AIKO_MQTT_TRANSPORT "tcp"
               :AIKO_NAMESPACE "aiko"
               :AIKO_BOOTSTRAP_UDP_PORT 4149})

(n/set-defaults! {})

(defn server-up? [host port]
  (try
    (let [socket (Socket. host port)]
      (.close socket)
      true)
    (catch Exception e
      false)))

(defn check-servers
  [servers]
  (or (some (fn [[host port]]
              (when (server-up? host port)
                [host port]))
            servers)
      (throw (Exception. "No servers available"))))



;; mqtt-hosts is an atom that holds a list of hosts (host, port)
(defn get-mqtt-hosts
  ([]           (check-servers (swap! mqtt-hosts conj
                                      (list (get defaults :AIKO_MQTT_HOST)
                                            (get defaults :AIKO_MQTT_PORT)))))
  ([host]      (check-servers (swap! mqtt-hosts conj
                                     (list host (get defaults :AIKO_MQTT_PORT)))))
  ([host port] (check-servers (swap! mqtt-hosts conj
                                     (list host port)))))


(n/defconfig mqtt-configuration
  {:hosts (or (System/getenv "AIKO_MQTT_HOST")
              (first (get-mqtt-hosts)))
   :port (or (System/getenv "AIKO_MQTT_PORT")
             (second (get-mqtt-hosts)))})

(def _LOCALHOST_IP "127.0.0.1")

(defn gethostname []
  (.getHostName (InetAddress/getLocalHost)))

(defn- get-lan-ip-address []
  (try
    (let [ip-address (first (filter (complement #{"127."})
                                    (map #(.getHostAddress %)
                                         (InetAddress/getAllByName (gethostname)))))]
      (or ip-address _LOCALHOST_IP))
    (catch Exception e
      (println "Aiko Services using localhost as your hostname")
      _LOCALHOST_IP)))

;; get the hostname of the machine
(defn gethostname []
  (or (System/getenv "AIKO_HOSTNAME")
      (gethostname)))

(defn- get-mqtt-port []
  (or (System/getenv "AIKO_MQTT_PORT")
      (get defaults :AIKO_MQTT_PORT)))

(defn get-namespace []
  (or (System/getenv "AIKO_NAMESPACE")
      (get defaults :AIKO_NAMESPACE)))

(defn get-namespace-prefix []
  (let [namespace (get-namespace)]
    (subs namespace 0 (inc (.indexOf namespace ":")))))


(defn get-pid 
  "get the process id for the running process" 
  []
 (.pid (ProcessHandle/current)))

(def config-file "resources/config.edn")

(defn load-resource-file [filename]
  (with-open [reader (io/reader (io/resource filename))]
    (slurp reader)))

(def config (load-resource-file config-file))

(defn get-config []
  (n/get-config config))


(defn get-mqtt-configuration []
  (get-in (get-config) [:mqtt]))


(defn get-mqtt-hosts [])
