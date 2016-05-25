(ns wonko-client.util
  (:import [java.util.concurrent
            ThreadPoolExecutor
            Executors
            TimeUnit
            ArrayBlockingQueue
            ThreadPoolExecutor$CallerRunsPolicy
            ThreadPoolExecutor$DiscardPolicy]
           [com.codahale.metrics MetricRegistry Timer ExponentiallyDecayingReservoir])
  (:require [clojure.tools.logging :as log]))

(def rejected-count (atom 0))

(def discard-and-log-policy
  (proxy [ThreadPoolExecutor$DiscardPolicy] []
    (rejectedExecution [^Runnable runnable ^ThreadPoolExecutor executor]
      (log/warn "rejected task. discarding runnable.")
      (proxy-super rejectedExecution runnable executor))))

(def caller-runs-and-logs-policy
  (proxy [ThreadPoolExecutor$CallerRunsPolicy] []
    (rejectedExecution [^Runnable runnable ^ThreadPoolExecutor executor]
      (swap! rejected-count inc)
      (log/warn "rejected task. caller is now executing runnable.")
      (proxy-super rejectedExecution runnable executor))))

(defn create-scheduled-tp [f rate]
  (doto (Executors/newScheduledThreadPool 1 )
    (.scheduleWithFixedDelay f 0 rate TimeUnit/MILLISECONDS)))

(defn create-fixed-threadpool [{:keys [thread-pool-size queue-size drop-on-reject?]}]
  (ThreadPoolExecutor. thread-pool-size
                       thread-pool-size
                       60
                       TimeUnit/SECONDS
                       (ArrayBlockingQueue. queue-size true)
                       (if drop-on-reject?
                         discard-and-log-policy
                         caller-runs-and-logs-policy)))

(defn stop-tp [^ThreadPoolExecutor tp]
  (when tp
    (.shutdownNow tp)))

(defn create-timer [metrics timer-name]
  (let [^ExponentiallyDecayingReservoir r (ExponentiallyDecayingReservoir. 1000000 0.015)
        t (Timer. r)]
    (.register metrics timer-name t)
    t))

(defmacro with-time [timer & body]
  `(let [context# (.time ~timer)
         result# ~@body]
     (.stop context#)
     result#))

(defn round-up-to-power-of-2 [x]
  (let [exp (- 32  (Integer/numberOfLeadingZeros (dec (int x))))]
    (int (Math/pow 2 exp))))
