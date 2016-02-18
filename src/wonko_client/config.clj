(ns wonko-client.config)

(defn consumer []
  {"zookeeper.connect" "localhost:2182"
   "group.id" "clj-kafka.consumer"
   "auto.offset.reset" "smallest"
   "auto.commit.enable" "false"})

(defn topic-streams []
  {"krikkit" 2})

(defn pager-duty []
  {:api-endpoint "https://events.pagerduty.com/generic/2010-04-15/create_event.json"
   :api-key "4bbd297e0f7e43cc948f7894b7d8ec7b"})

(defn alert-thread-pool-size []
  2)

(defn log []
  {:root                     "/var/log/wonko"
   :thread-count             1
   :thread-prefix            "Wonko-Log-"
   :filename-prefix          "wonko"
   :default-context          "wonko::"
   :rotate-every-minute      120
   :max-msg                  10000
   :max-unflushed            10000
   :max-elapsed-unflushed-ms 3000
   :queue-timeout-ms         1000})
