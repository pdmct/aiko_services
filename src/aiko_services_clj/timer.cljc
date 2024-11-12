(ns aiko-services-clj.timer
  (:require [clojure.core.async :refer [go timeout alt! chan close!]]
            [clojure.tools.logging :as logging :refer [info warn]])
  (:import [java.lang System]))

;; this module is a simple timer module that can be used to create timers and manage a set of timeouts
;; the module is implemented as a protocol and a type that implements the protocol
;; 
;; the protocol ITimer has three functions:
;; get-current-time returns the current time in milliseconds
;; set-timeout takes a function and a time-out in milliseconds and returns an id
;; cancel-timeout takes an id and cancels the timeout
;;
;; note: functions and timeouts are implemented using core.async channels
;; and local to this process


;; create a singelton atom to store all the timeout ids and timers
(def timeout-state (atom {:last-id 0
                        :timers {}}))

(defn cancellable-timeout
  "Create a timeout that can be cancelled, timeout is in milliseconds"
  [ms id callback]
  (let [cancel-chan (chan)]
    (go
      (alt!
        (timeout ms) ([_] (do
                            (info "Timeout fired")
                            (swap! timeout-state update :timers dissoc id)
                            (callback)))
        cancel-chan ([_] (info "Timeout cancelled"))))
    cancel-chan))


(defprotocol ITimer
  ;; get-current-time returns the current time in milliseconds
  (get-current-time [this])
  ;; set-timeout takes a function and a time-out in milliseconds and returns an id
  (set-timeout [this f time-out])
;; cancel-timeout takes an id and cancels the timeout
  (cancel-timeout [this id]))


(deftype Timer []
  ITimer
  (get-current-time [this]
    (System/currentTimeMillis))
  (set-timeout [this f time-out]
    (let [id (:last-id (swap! timeout-state update :last-id inc))]
      (swap! timeout-state update :timers assoc id (cancellable-timeout time-out id f))
      id))
  (cancel-timeout [this id]
    (let [timers @timeout-state]
      (if (contains? (:timers timers) id)
        (do
          (close! ((:timers timers) id))
          (swap! timeout-state update :timers dissoc id))
        (warn "Timeout not found")))))
