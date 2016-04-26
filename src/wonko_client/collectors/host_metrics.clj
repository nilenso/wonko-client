(ns wonko-client.collectors.host-metrics
  (:require [clojure.java.jmx :as jmx]
            [clojure.string :as s]
            [wonko-client.core :as client]
            [clojure.tools.logging :as log]))

(defn mbean-names []
  (map #(str %) (jmx/mbean-names "*:*")))

(defn group-metrics-by-name [re]
  (->> (mbean-names)
       (filter #(re-find re %))
       (map (fn [n] (let [m (jmx/mbean n)] [(:Name m) m])))
       (into {})))

(defn threading []
  {:prefix "threading"
   :metrics (jmx/mbean "java.lang:type=Threading")
   :paths [[:DaemonThreadCount]
           [:PeakThreadCount]
           [:ThreadCount]
           [:TotalStartedThreadCount]]
   :property-names []})

(defn cpu []
  {:prefix "cpu"
   :metrics (jmx/mbean "java.lang:type=OperatingSystem")
   :paths [[:AvailableProcessors]
           [:CommittedVirtualMemorySize]
           [:FreePhysicalMemorySize]
           [:FreeSwapSpaceSize]
           [:OpenFileDescriptorCount]
           [:ProcessCpuLoad]
           [:ProcessCpuTime]
           [:SystemCpuLoad]
           [:SystemLoadAverage]
           [:TotalPhysicalMemorySize]
           [:TotalSwapSpaceSize]]
   :property-names []})

(defn memory []
  {:prefix "memory"
   :metrics (jmx/mbean "java.lang:type=Memory")
   :paths [[:HeapMemoryUsage :committed]
           [:HeapMemoryUsage :init]
           [:HeapMemoryUsage :max]
           [:HeapMemoryUsage :used]
           [:NonHeapMemoryUsage :committed]
           [:NonHeapMemoryUsage :init]
           [:NonHeapMemoryUsage :max]
           [:NonHeapMemoryUsage :used]]
   :property-names ["memory-type"]})

(defn memory-pools []
  (let [metrics (group-metrics-by-name #"MemoryPool")]
    {:prefix "memory-pool"
     :metrics metrics
     :paths (mapcat (fn [pool-name]
                      [[pool-name :CollectionUsage :committed]
                       [pool-name :CollectionUsage :max]
                       [pool-name :CollectionUsage :used]
                       [pool-name :PeakUsage :committed]
                       [pool-name :PeakUsage :max]
                       [pool-name :PeakUsage :used]
                       [pool-name :Usage :committed]
                       [pool-name :Usage :max]
                       [pool-name :Usage :used]])
                    (keys metrics))
     :property-names ["pool" "usage-type"]}))

(defn garbage-collection []
  (let [metrics (group-metrics-by-name #"Garbage")]
    {:prefix "gc"
     :metrics metrics
     :paths (mapcat (fn [gc-type]
                      [[gc-type :LastGcInfo :GcThreadCount]
                       [gc-type :LastGcInfo :duration]
                       [gc-type :CollectionCount]
                       [gc-type :CollectionTime]])
                    (keys metrics))
     :property-names ["gc-type" "gc-info"]}))

(defn ->wonko [{:keys [prefix metrics paths property-names]}]
  (for [path paths
        :let [metric-value (get-in metrics path)
              metric-name (s/join "-" ["jvm" prefix (name (last path))])
              property-values (map name (drop-last path))
              properties (zipmap property-names property-values)]
        :when (some? metric-value)]
    {:metric-name metric-name
     :properties properties
     :metric-value metric-value}))

(defn metrics []
  (->> [(threading) (cpu) (memory) (memory-pools) (garbage-collection)]
       (map ->wonko)
       (apply concat)))

(defn send-metrics []
  (doseq [{:keys [metric-name properties metric-value] :as metric} (metrics)]
    (client/gauge metric-name properties metric-value)))
