(ns aiko-services-clj.utilities.parse
  (:require [clojure.string :as str])
  (:import [java.lang Long Double Integer Exception]))

(defn parse-length-prefixed-data
  "Parses length-prefixed binary/byte data."
  [tokens]
  (let [[length-token & the-rest] tokens
        length (Integer/parseInt length-token)]
    (if (< (count (first the-rest)) length)
      (throw (Exception. "Insufficient data for length-prefixed content"))
      (let [data (subs (first the-rest) 0 length)
            remaining (vec (cons (subs (first the-rest) length) (rest the-rest)))]
        [data remaining]))))

(defn parse-csexp
  "Parses canonical S-expressions into an AST."
  [tokens]
  (letfn [(inner [tokens]
            (when (seq tokens)
              (let [[token & the-rest] tokens
                    _ (println (str "token: " token " rest: " the-rest))
                    _ (println (str "eq: " (= token "(") " token: " token))]
                (cond
                  ;; Start of a list
                  (= token "(")
                  (let [[elements remaining] (loop [acc [] tokens the-rest]
                                               (let [[elem next-tokens] (inner tokens)]
                                                 (if (= (first next-tokens) ")")
                                                   [(conj acc elem) (rest next-tokens)]
                                                   (recur (conj acc elem) next-tokens))))
                        _ (println (str "elements: " elements " remaining: " remaining))]
                    [elements remaining])

                  ;; End of a list
                  (= token ")")
                  [nil the-rest]

                  ;; Length-prefixed binary data
                  (re-matches #"\d+:.*" token)
                  [(parse-length-prefixed-data (cons (subs token 0 (dec (count token))) the-rest))
                   the-rest]


                  ;; Normal atom (symbol or number)
                  :else
                  [(try
                     (Long/parseLong token)    ; Parse as integer if possible
                     (catch Exception _
                       (try
                         (Double/parseDouble token) ; Parse as float if possible
                         (catch Exception _
                           (symbol token)))))       ; Treat as symbol otherwise
                   the-rest]))))]
    (first (inner tokens))))


(defn split-fixed-length-strings
  "Finds strings with a length prefix and splits them into a list of strings and prefixes."
  [xs]
  (map (fn [s]
         (if-let [prefix (second (re-matches #"(\d+:)?(.*)" s))]
           (let [prefix-val (if (re-matches #"\d+:" prefix)
                              (Integer/parseInt (subs prefix 0 (dec (count prefix))))
                              nil)
                 prefix-val (if (and prefix-val
                                     (> prefix-val (- (count s) (count prefix))))
                                     (- (count s) (count prefix))
                                     prefix-val)
                 first-part   (subs s (count prefix) (+ (count prefix) prefix-val))
                 rest       (when (not (> (+ (count prefix) prefix-val) (count s)))
                              (subs s (+ (count prefix) prefix-val)))]

             (if rest
               [first-part rest]
               [first-part]))
             [s]))
       xs))

(defn tokenize
  "Converts a string of canonical S-expressions into tokens."
  [input]
  (-> input
      (str/replace "(" " ( ")
      (str/replace ")" " ) ")
      (str/split #"\s+")
      ((fn [s] (remove str/blank? s)))
      (split-fixed-length-strings)
      flatten))

(defn parse
  "Parses a string of canonical S-expressions into an AST."
  [input]
  (parse-csexp (tokenize input)))