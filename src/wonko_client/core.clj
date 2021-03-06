(ns wonko-client.core
  (:require [clojure.tools.logging :as log]
            [wonko-client.disruptor :as disruptor]
            [wonko-client.kafka-producer :as kp]
            [wonko-client.message :as message]
            [wonko-client.message.validation :as v]))

(defn default-exception-handler [e message response]
  (log/error e {:message message :response response :e (bean e)}))

(def ^:private default-options
  {:validate?         false
   :drop-on-reject?   false
   :exception-handler default-exception-handler
   :worker-count      10
   :queue-size        10
   :topics            {:events "wonko-events"
                       :alerts "wonko-alerts"}})

(defonce instance
  {:service nil
   :topics nil
   :queue nil
   :producer nil})

(defn counter [metric-name properties & {:as options}]
  (->> :counter
       (message/build (:service instance) metric-name properties nil options)
       (disruptor/send-async instance :events)))

(defn gauge [metric-name properties metric-value & {:as options}]
  (->> :gauge
       (message/build (:service instance) metric-name properties metric-value options)
       (disruptor/send-async instance :events)))

(defn stream [metric-name properties metric-value & {:as options}]
  (->> :stream
       (message/build (:service instance) metric-name properties metric-value options)
       (disruptor/send-async instance :events)))

(defn alert [alert-name alert-info]
  (->> :counter
       (message/build-alert (:service instance) alert-name {} nil alert-name alert-info nil)
       (kp/send-message instance :alerts)))

(defn init! [service-name kafka-config & {:as user-options}]
  (let [options (merge default-options user-options)
        {:keys [validate? topics]} options]
    (alter-var-root #'instance
                    (constantly
                     {:service service-name
                      :topics topics
                      :queue (disruptor/init options)
                      :producer (kp/create kafka-config)
                      :exception-handler (:exception-handler options)}))
    (v/set-validation! validate?)
    (log/info "wonko-client initialized" instance)
    nil))

(defn terminate! []
  (disruptor/terminate instance)
  (kp/close instance)
  nil)
