# Run a measured scale and resilience experiment

Type: future-work
Status: future
Milestone: Later Backlog
Blocked by: Core Acceptance from 12
Source decisions: 07, 10, 11

## Outcome

Measure where the simple single-instance Core stops meeting an explicitly chosen workload, change one bottleneck at a time, and retain reproducible before/after evidence.

## Why deferred

Core needs credible correctness and a public demo, not infrastructure theatre. Redis, PostGIS, WebFlux, tracing backends and multiple instances have no justified role until the simple system is measured under a load the portfolio can explain.

## Experiment

- Reproduce the earlier reference scenario of up to 2,000 On Duty Couriers, 1,000 Active Deliveries, 2,000 tracking sessions and approximately 200 location requests per second.
- Capture API and location-to-view latency, errors, CPU, memory, database pool, SSE connections and lost/duplicate outcomes.
- Profile before selecting a component; write a short ADR for each accepted substitution.
- Rerun the same workload and one restart/failure drill after the change.

## Candidate substitutions

- Redis latest-value TTL plus Pub/Sub only for proven multi-instance/fan-out or in-memory pressure; persistence and Streams remain off.
- PostGIS only when polygon/distance query work is the measured matching bottleneck.
- WebFlux only when tuned MVC streaming is the measured connection bottleneck and relevant I/O can become non-blocking.
- OpenTelemetry export only when a real backend and diagnostic question exist.

## Acceptance

- Results include honest failures as well as improvements.
- No experiment creates durable raw coordinate history or changes product semantics.
- Core remains the default deployment if it already meets the selected target.
- Added infrastructure has a documented removal/rollback path and measured benefit.

## Not included

An uptime SLA, Kubernetes, microservices, global multi-region deployment or unmeasured technology adoption.
