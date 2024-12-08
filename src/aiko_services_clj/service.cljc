(ns aiko-services-clj.service
  (:require [clojure.string :as str])
  (:refer-clojure :exclude [send])
  (:import [java.lang Exception]))


;; ServiceTag functions
(defn parse-tags-string
  "parses a string of tags into a map
   returns: a map of tags
   tags_string is a string of tags in the format tag1=value1,tag2=value2,tag3=value3"
  [tags_string]
  (let [tags (str/split tags_string #",")]
    (reduce (fn [acc tag]
              (let [tag_parts (str/split tag #"=")]
                (assoc acc (first tag_parts) (second tag_parts))))
            {}
            tags)))

(defn get-tag-value
  "gets the values for a given tag name.
   returns: value or empty string if the tag is not found
   name is the name of the tag
   tags is a map of tags"
  [name tags]
  (get tags name ""))

(defn match-tags
  "matches a set of tags against a set of required tags
   returns: true if all required tags are found in the tags map
   tags is a map of tags
   required_tags is a map of required tags"
  [tags required_tags]
  (every? (fn [[k v]] (= v (get tags k))) required_tags))


(defprotocol IService
  (add-message-handler [this message_handler topic binary=False])
  (remove-message-handler [this message_handler topic])
  (add-registrar-handler [this registrar_handler])
  (run [this])
  (stop [this])
  (add-tags [this tags])
  (add-tags-string [this tags_string])
  (get-tags-string [this])
  (set-registrar-handler [this registrar_handler]))

;; (defprotocol IService
;;   (start [this])
;;   (send [this event])
;;   (state [this])
;;   (add-listener [this id listener])
;;   (reload [this fsm]))

(deftype Service [time_started name protocol tags transport message_handlers registrar_handlers topics]
  IService
  (add-message-handler [this message_handler topic binary=False]
    (throw (Exception. "Not implemented")))
  (remove-message-handler [this message_handler topic]
    (throw (Exception. "Not implemented")))
  (add-registrar-handler [this registrar_handler]
    (throw (Exception. "Not implemented")))
  (run [this]
    (throw (Exception. "Not implemented")))
  (stop [this]
    (throw (Exception. "Not implemented")))
  (add-tags [this tags]
    (for [tag tags]
      (.-tags this tag)))
  (add-tags-string [this tags_string]
    (throw (Exception. "Not implemented")))
  (get-tags-string [this]
    (throw (Exception. "Not implemented")))
  (set-registrar-handler [this registrar_handler]
    (throw (Exception. "Not implemented"))))


(defn service []
  (new Service (System/currentTimeMillis) "service" nil nil nil nil nil nil))



