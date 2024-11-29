(ns aiko-services-clj.connection)


;; connection state maintenance functions
(def connection-state-valid #{:none :network :bootstrap :transport :registrar})
(def connection-state-index {:none 0 :network 1 :bootstrap 2 :transport 3 :registrar 4})
(def connection-state (atom {:state :none
                             :handlers []}))


(defn create-connection
  "Creates a new connection state."
  []
  (swap! connection-state
         (fn [state]
           (assoc state
                  :state :none
                  :handlers []))))


(defn get-connection-state
  "Returns the current connection state."
  []
  (get @connection-state :state))

(defn connection-state-valid?
  "Returns true if the connection state is valid."
  [state]
  (connection-state-valid state))

(defn set-connection-state
  "Sets the connection state."
  [new-state]
  {:pre [(connection-state-valid? new-state)]}
  (swap! connection-state
         (fn [state]
           (assoc state
                  :state new-state))))

(defn add-connection-state-handler
  "Adds a connection state handler."
  
  [handler]
  {:pre [(fn? handler)]}
  (handler {:state @connection-state})
  (swap! connection-state
         (fn [state]
           (when (not (contains? (:handlers state) handler))
             (assoc state
                    :handlers (conj (:handlers state) handler))))))

(defn remove-connection-state-handler
  "Removes a connection state handler."
  [handler]
  {:pre [(fn? handler)]}
  (swap! connection-state
         (fn [state]
           (assoc state
                  :handlers (remove #(= % handler) (:handlers state))))))

(defn is-connected?
  "Returns true if the connection state is connected."
  [state]
    (>= (connection-state-index state) 
        (connection-state-index (get-connection-state))))

(defn update-connection-state
  "Updates the connection state, calls all handlers with the new state."
  [new-state]
  {:pre [(connection-state-valid? new-state)]}
  (set-connection-state new-state)
  (doseq [handler (:handlers @connection-state)]
    (handler new-state)))
