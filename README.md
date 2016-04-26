# Wonko Client

Use this to publish monitoring events to Wonko from your project.

## Installation

Add leiningen dependency:
```clojure
[staples-sparx/wonko-client "0.1.4"]
```

It is hosted in SparX s3 maven, so you'll have to add this to `project.clj`:
```
:repositories {"runa-maven-s3" {:url "s3p://runa-maven/releases/"
                                :username [:gpg :env/archiva_username]
                                :passphrase [:gpg :env/archiva_passphrase]}}
```
## Usage

Initialize the client with the service's name, kafka producer configuration and optionally an exception handler. Validation is turned off by default (for performance reasons), but you can enable validation in dev and test environments to ensure inputs are correct.

```clojure
(require '[wonko-client.core :as wonko])

(wonko/init! "service-name"
             {"bootstrap.servers" "127.0.0.1:9092"
              "compression.type" "gzip"
              "linger.ms" 5
              "block.on.buffer.full" "false"
              "total.memory.bytes" (* 1024 1024 120)}
             :exception-handler (fn [response exception]
                                  (prn response exception))
             :validate? true)
```

Send monitoring events using `counter`s, `gauge`s, and `stream`s.
```clojure
(wonko/counter :event-occurred nil)
(wonko/counter :job-ended {:status :start})

(wonko/gauge :job-stats {:result :success} 107)
(wonko/gauge :thread-pool-size 42)

(wonko/stream :api-call {:status "200"} 5)
(wonko/stream :api-call {:status "400"} 7)
```

Send alerts to pager-duty, email or slack using `alert`s.
```clojure
(wonko/alert :some-alert-name {:alert :info})
```

### Collectors

A few collectors are built in. For example, to start collecting and sending host-metrics and ping, use:
```clojure
(require '[wonko-client.collectors :as wc])
(wc/start :host-metrics :ping)
```

Currently, there are only two kinds of collectors: `:host-metrics` and `:ping`. `:ping` is used as a heartbeat counter to monitor service uptime.

## Options

- `validate?`: Set this to true in dev environments to synchronously validate schemas of arguments to wonko metrics. Wonko-client will throw schema exception IllegalArgumentException with a description of the errors. Default value is `false`.
- `thread-pool-size` and `queue-size`: These are the configs for a fixed threadpool within wonko that makes a few things asynchronous. Typically, you wouldn't need to tune these. Default values are `10` and `10` respectively.
- `:drop-on-reject?`: Set this to true if slowing down the service in case of a problem with wonko-client/kafka is unacceptable. This options allows you to choose between dropping metrics and adding back pressure to the application. Alerts are synchronous however, so they will not be dropped. The thread-pool itself can be tuned such that under normal conditions, no metrics will be dropped.

## Metric types
### Counter
A counter is a simple incrementing number. It only ever goes up. You can compute the rate (number of events per second) at which a counter is changing, and that is usually more useful than the value itself.

They are useful for counting things like requests, task started/ended, errors/alerts, etc. Consider adding counters along with log statements, and failure occurrences. If the value can go down, pick a gauge.
### Gauge
A gauge is a numerical value that can go up or down. Consider using a gauge to monitor in-progress requests, queue size, current thread count, pending jobs, batch-job timing, etc. Averages and rates of gauges are usually meaningless.

Not every value of a gauge is reported on (because prometheus polls the data from wonko, gauge changes between polls are lost). If you want to track a series of values, use a Stream instead.
### Stream
A stream is series of values, which are observations of a metric. All values in a stream are used for sampling and aggregating. You can use streams to compute rate, distributions/quantiles and aggregates.

Typically, request latencies, feed lengths, SLA computations, and any kind of performance measurement would warrant a stream.

## Properties
These are characteristics of events being monitored. URIs, response statuses, different stages in pieline, etc can be tracked using properties. You can filter metrics using properties, and aggregate across them.

Ensure that property values are a bounded set. Properties are intended to be metadata for a given metric - if you want to send unbounded _values_ to Wonko, make them the primary metric value.
For example, "Response Status Code" can be a property, since there are a finite number of values it can take. "Response time" should not be a property, but a primary metric value.

## Alerts
An alert is used to notify people via pager-duty, email or slack. Consider using this for any failure scenario for which you want to be notified. An alert is also implicitly a counter, so you can use it to get stats on alerts over time.

For (more comprehensive) alerts based on statistical or historical data, consider configuring them through prometheus.

## License
Copyright © 2016 Staples Sparx.
Released under the MIT license.
http://mit-license.org/
