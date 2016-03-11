(ns wonko-client.validation-test
  (:require [wonko-client.validation :as v]
            [clojure.test :refer :all]
            [wonko-client.test-fixtures :as tf])
  (:import [clojure.lang ExceptionInfo]))

(use-fixtures :each tf/with-cleared-validation-state)

(defn make-message [{:keys [service metric-name properties
                            metric-type metric-value] :as message}]
  (merge {:service (str (gensym "test-service-"))
          :metric-name (str (gensym "test-metric-name-"))
          :metric-value nil
          :metric-type :counter
          :properties {"first" 1 "second" 2}
          :metadata {:host "localhost"
                     :ip-address "127.0.0.1"
                     :ts 9999999999}}
         message))

(deftest validate-label-names-test
  (testing "valid when label names have not changed"
    (is (nil? (v/validate! (make-message {:service "label-names" :metric-name "not-changed"
                                          :properties {"first" 1}}))))
    (is (nil? (v/validate! (make-message {:service "label-names" :metric-name "not-changed"
                                          :properties {"first" 1}})))))

  (testing "not valid when label names have been added"
    (is (nil? (v/validate! (make-message {:service "label-names" :metric-name "added"
                                          :properties {"first" 1}}))))
    (is (thrown-with-msg?
         IllegalArgumentException #"Cannot change the label names for a metric."
         (v/validate! (make-message {:service "label-names" :metric-name "added"
                                     :properties {"first" 3 "second" 4}})))))

  (testing "not valid when label names have been removed"
    (is (nil? (v/validate! (make-message {:service "label-names" :metric-name "removed"
                                          :properties {"first" 3 "second" 4}}))))
    (is (thrown-with-msg?
         IllegalArgumentException #"Cannot change the label names for a metric."
         (v/validate! (make-message {:service "label-names" :metric-name "removed"
                                     :properties {"first" 3}})))))

  (testing "metrics with the same name but different type are considered different metrics"
    (is (nil? (v/validate! (make-message {:service "label-names" :metric-name "removed"
                                          :metric-type :counter :properties {"first" 3 "second" 4}}))))
    (is (nil? (v/validate! (make-message {:service "label-names" :metric-name "removed"
                                          :metric-value 3
                                          :metric-type :gauge :properties {"first" 1}}))))))

(deftest validate-metric-attributes
  (testing "metric value should be a number"
    (is (nil? (v/validate! (make-message {:service "test-service" :metric-name "test-metric"
                                          :metric-value 3
                                          :metric-type :gauge}))))
    (is (thrown?
         ExceptionInfo
         (v/validate! (make-message {:service "test-service" :metric-name "test-metric"
                                     :metric-value "x"
                                     :metric-type :gauge})))))

  (testing "property value should be a scalar, not a collection"
    (is (thrown?
         ExceptionInfo
         (v/validate! (make-message {:properties {"first" [3] "second" {:x 5}}})))))

  (testing "metric value for gauges and streams can not be nil"
    (is (thrown?
         ExceptionInfo
         (v/validate! (make-message {:metric-value nil
                                     :metric-type :gauge}))))
    (is (thrown?
         ExceptionInfo
         (v/validate! (make-message {:metric-value nil
                                     :metric-type :stream}))))
    (is (nil?
         (v/validate! (make-message {:metric-value nil
                                     :metric-type :counter})))))

  (testing "property names should be strings or keywords"
    (is (thrown?
         ExceptionInfo
         (v/validate! (make-message {:properties {999 3 :second 4 "third" 5}}))))
    (is (nil?
         (v/validate! (make-message {:properties {"first" 3 :second 4 "third" 5}})))))

  (testing "label values are not set when validation fails"
    (is (thrown?
         ExceptionInfo
         (v/validate! (make-message {:service "test-service" :metric-name "fail-metric"
                                     :metric-value "x" :metric-type :gauge}))))
    (is (nil? (v/validate! (make-message {:service "test-service" :metric-name "fail-metric"
                                          :metric-value 3 :metric-type :gauge})))))

  (testing "metric-name should be valid"
    (is (thrown?
         ExceptionInfo
         (v/validate! (make-message {:metric-name 6}))))
    (is (thrown?
         ExceptionInfo
         (v/validate! (make-message { :metric-name :invalid-name?}))))))
