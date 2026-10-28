# Spring Boot Actuator + Prometheus + Grafana

A small Java 21 application for learning the full metrics flow:

`Nginx -> 2 x Spring Boot -> Actuator -> Prometheus -> Grafana`

## Start everything

```bash
docker compose up --build
```

Wait about one minute, then open:

- Application: <http://localhost:8080/api/greeting>
- Direct app-1: <http://localhost:8081/api/greeting>
- Direct app-2: <http://localhost:8082/api/greeting>
- Actuator health: <http://localhost:8080/actuator/health>
- Raw Prometheus metrics: <http://localhost:8080/actuator/prometheus>
- Prometheus: <http://localhost:9090> (query `up`)
- Grafana: <http://localhost:3000> (`admin` / `admin`)

Grafana automatically loads **Spring Boot Learning / Spring Boot Metrics Overview**.
The traffic generator repeats a 60-second cycle: trough, rising, peak, falling.
Port `8080` is the Nginx round-robin entry point. Ports `8081` and `8082` access
each application directly. Prometheus scrapes each
instance directly, and the Grafana `instance` selector lets you compare them.

Stop and remove containers:

```bash
docker compose down
```

Add `-v` to also delete saved Grafana and Prometheus data.

## APIs

```bash
curl 'localhost:8080/api/greeting?name=Prometheus'
curl 'localhost:8080/api/compute?size=50000'
curl -X POST 'localhost:8080/api/orders'
curl -X POST 'localhost:8080/api/memory-pressure?megabytes=32'
curl -X DELETE 'localhost:8080/api/memory-pressure'
```

`POST /api/orders` intentionally returns about 10% HTTP 503 responses, so you can
observe an error rate. Traffic is intentionally unbalanced: 50% greeting, 25%
compute, 15% memory pressure, and 10% orders.

The memory endpoint creates **allocation churn** (short-lived objects) and retains
25% of each allocation, up to 80 MiB. The container JVM uses a 256 MiB heap, so
the Grafana dashboard clearly shows heap growth and **GC pressure** without
unbounded memory use. `DELETE /api/memory-pressure` releases the retained objects.

## Run with Gradle

Java 21 is required:

```bash
./gradlew bootRun
```

In another terminal:

```bash
python3 scripts/generate_load.py
```

Run tests with `./gradlew test`.

## PromQL to try

Request rate (RPS, **requests per second**):

```promql
sum by (uri) (rate(http_server_requests_seconds_count{uri=~"/api/.*"}[1m]))
```

95th percentile latency (**p95 latency**):

```promql
histogram_quantile(0.95,
  sum by (le, uri) (rate(http_server_requests_seconds_bucket{uri=~"/api/.*"}[2m]))
)
```

Custom order rate:

```promql
sum by (result) (rate(demo_orders_total[2m]))
```

Memory allocation rate and GC pause time:

```promql
rate(jvm_gc_memory_allocated_bytes_total[1m])
rate(jvm_gc_pause_seconds_sum[1m])
```

Memory remaining after GC (**live set**) and its percentage of old-generation
capacity:

```promql
jvm_gc_live_data_size_bytes
100 * jvm_gc_live_data_size_bytes / clamp_min(jvm_gc_max_data_size_bytes, 1)
```
