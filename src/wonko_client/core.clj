(ns wonko-client.core
  (:require [wonko-client.kafka-producer :as kp]
            [wonko-client.util :as util]
            [wonko-client.message :as message]
            [wonko-client.validation :as v]
            [clojure.tools.logging :as log]))

(def ^:private default-options
  {:validate?        false
   :thread-pool-size 10
   :queue-size       10
   :topics           {:events "wonko-events"
                      :alerts "wonko-alerts"}})

(defonce ^:private instance
  (atom {:service nil
         :topics nil
         :thread-pool nil
         :producer nil}))

(defn- send [{:keys [thread-pool topics producer] :as instance} topic message]
  (.submit thread-pool #(kp/send producer message (get topics topic))))

(defn counter [metric-name properties & {:as options}]
  (->> :counter
       (message/build (:service @instance) metric-name properties nil options)
       (send @instance :events)))

(defn gauge [metric-name properties metric-value & {:as options}]
  (->> :gauge
       (message/build (:service @instance) metric-name properties metric-value options)
       (send @instance :events)))

(defn stream [metric-name properties metric-value & {:as options}]
  (->> :stream
       (message/build (:service @instance) metric-name properties metric-value options)
       (send @instance :events)))

(defn alert [alert-name alert-info]
  (->> :counter
       (message/build-alert (:service @instance) alert-name {} nil alert-name alert-info nil)
       (send @instance :alerts)))

(defn set-topics! [events-topic alerts-topic]
  (swap! instance assoc :topics {:events events-topic
                                 :alerts alerts-topic}))

(defn init! [service-name kafka-config & {:as user-options}]
  (let [options (merge default-options user-options)
        {:keys [validate? thread-pool-size queue-size topics]} options]
    (reset! instance
            {:service service-name
             :topics topics
             :thread-pool (util/create-fixed-threadpool thread-pool-size queue-size)
             :producer (kp/create kafka-config options)})
    (v/set-validation! validate?)
    (log/info "wonko-client initialized" instance)
    nil))
