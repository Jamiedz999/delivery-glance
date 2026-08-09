# Evaluate a durable domain-event backbone only when a consumer exists

Type: future-work
Status: future
Milestone: Later Backlog — unscheduled
Blocked by: a new independent durable consumer and Core Acceptance from 12
Source decisions: 07, 10, 11

## Outcome

Make Kafka—or another durable broker—a consequence of a real independent consumer and replay requirement, never a portfolio checklist item.

## Why deferred

The Core has one process. Local after-commit notifications can refresh views, and PostgreSQL reconstructs current durable truth after loss or restart. Kafka would add deployment, schemas, retention, ordering, retry and duplicate-consumption work without an existing consumer that benefits from it.

## Entry trigger

This Issue may enter a Sprint only when all are named:

- at least one independent service or process that cannot use the Core transaction/read model;
- the non-coordinate domain event it consumes;
- why durable replay is required rather than current-state reread;
- ownership, ordering and retention boundaries; and
- an outbox plus idempotent-consumer failure design.

Without those facts, close the evaluation as “not needed” rather than installing Kafka.

## Acceptance if triggered

- Publish committed non-coordinate domain events from a transactional outbox.
- Version schemas and prove duplicate, reorder, retry, poison-message and broker-outage behaviour.
- Keep PostgreSQL as Delivery/Assignment truth; a consumer cannot redefine lifecycle state.
- Measure the operational and recovery benefit against the broker-free baseline.
- Document rollback and retention.

## Permanent prohibition

Raw Courier location reports, Current Location snapshots and any replayable coordinate stream never enter Kafka. Broker retention and replay would violate the no-Route-History boundary even if no UI exposed it.
