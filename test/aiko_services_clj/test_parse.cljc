(ns aiko-services-clj.test-parse
  (:require [clojure.test :refer [deftest is testing]]
            [aiko-services-clj.utilities.parse :as parse]))

(deftest test-parse-length-prefixed-data
  (testing "parse-length-prefixed-data with sufficient data"
    (is (= ["data" ["remaining"]]
           (parse/parse-length-prefixed-data ["4" "dataremaining"]))))
  (testing "parse-length-prefixed-data with insufficient data"
    (is (thrown? Exception
                 (parse/parse-length-prefixed-data ["10:short"])))))

(deftest test-parse-csexp
  (testing "parse-csexp with simple list"
    (is (= ['(1 2 3)]
           (parse/parse-csexp ["(" "1" "2" "3" ")"]))))
  (testing "parse-csexp with nested list"
    (is (= ['(1 (2 3) 4)]
           (parse/parse-csexp ["(" "1" "(" "2" "3" ")" "4" ")"]))))
  (testing "parse-csexp with length-prefixed data"
    (is (= ["data"]
           (parse/parse-csexp ["4" "data"])))))

