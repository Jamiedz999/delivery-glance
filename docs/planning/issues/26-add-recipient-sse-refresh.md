# DG-026 · Add Recipient SSE refresh and reconnect

Type: implementation
Sprint: 3
Area: realtime, full-stack
Blocked by: DG-025
Estimate: 2–3 focused hours

## Outcome

An open Recipient page automatically refreshes authorised current truth after accepted Delivery or location changes, reconnects safely after interruption, and never treats the SSE stream as the source of truth.

## Read first

- [Core technical baseline](../implementation/TECHNICAL-BASELINE.md)
- [Ticket 12: Recipient SSE-only scope](../12-rescope-to-resume-ready-core.md#tracking-link-and-recipient-view)
- [Architecture research: authoritative views and SSE](../research/core-technical-architecture.md#5-http-writes--sse-read-updates)

## In scope

- Add `GET /api/recipient/events` for a valid Tracking grant using Spring MVC `SseEmitter` and a bounded executor/registry.
- Events are small scoped invalidation hints such as `snapshot-changed` with a monotonic view version. They contain no coordinates, address, Courier identity, raw token or domain event history.
- Accepted state transitions and accepted latest-location changes notify only connected grants for the affected Delivery after the relevant change succeeds. Rejected reports and rolled-back commands emit nothing.
- On every connect, heartbeat and snapshot refetch, recheck grant scope and effective expiry. Close unauthorised/expired streams without revealing why.
- The React page opens same-origin `EventSource`, invalidates its Recipient snapshot query on a hint, shows Connected/Reconnecting separately from Location Freshness and fetches a fresh snapshot after reconnect.
- Memory is bounded: remove completed, timed-out and disconnected emitters; application restart may lose emitters because reconnect reconstructs truth.

## Acceptance criteria

- Integration tests prove the correct Delivery receives a hint after commit and another Delivery never does.
- Tests prove rejected/out-of-order locations and rolled-back transitions emit no hint.
- Reconnect tests prove a missed event still yields the latest authorised snapshot and no replay/event database is required.
- Expired grants cannot connect or continue through heartbeat/refetch; emitter cleanup leaves no registry leak.
- Frontend tests keep timestamped content while reconnecting, distinguish connection from freshness and refetch once after reconnection.
- The cumulative three-role browser journey runs from Tracking Link open through assignment, pickup, location change and Delivered without manual Recipient refresh.

## Non-goals

- WebSocket, WebFlux, Redis fan-out, Kafka, durable event replay or exactly-once delivery.
- SSE for Dispatcher/Courier pages or coordinate payloads in events.
- Performance claims beyond the small Core demo.

## PR evidence

Include reconnect/isolation test results and one network trace showing hint then authorised snapshot refetch. After merge, run the Sprint 3 release gate: this is the first MVP. Promote only DG-027.
