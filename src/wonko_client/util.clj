(ns wonko-client.util
  (:import [java.util.concurrent
            ThreadPoolExecutor
            TimeUnit
            ArrayBlockingQueue
            ThreadPoolExecutor$CallerRunsPolicy]))

(defn start-daemon [f sleep-ms]
  (doto (Thread. (fn []
                   (f)
                   (Thread/sleep sleep-ms)
                   (recur)))
    (.setDaemon true)
    (.start)))

(defn stop-daemon [daemon]
  (.interrupt ^Thread daemon))

(defn create-fixed-threadpool [pool-size queue-size]
  (ThreadPoolExecutor. pool-size  ;corePoolSize
                       pool-size  ;maximumPoolSize
                       60
                       TimeUnit/SECONDS
                       (ArrayBlockingQueue. queue-size true)
                       (ThreadPoolExecutor$CallerRunsPolicy.)))
