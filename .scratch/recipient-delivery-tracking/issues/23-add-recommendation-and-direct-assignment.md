# DG-023 · Add nearest recommendation and atomic Direct Assignment

Type: implementation
Status: blocked
Labels: blocked, core, sprint-2, backend, full-stack
Blocked by: DG-022
Estimate: 5–6 focused hours

## Outcome

A Dispatcher sees up to the three nearest currently Eligible Couriers, directly assigns one, and the assigned Courier progresses the Delivery through pickup and handoff. Database constraints and a real race test prove that one Courier or Delivery cannot receive two active Assignments.

## Read first

- [Core technical baseline](../implementation/TECHNICAL-BASELINE.md)
- [Ticket 12: simplified Delivery and Assignment](12-rescope-to-resume-ready-core.md#delivery-and-assignment)
- [Full recommendation decision, using its Core scope update](04-define-courier-recommendation-and-assignment.md)
- [Core flow logic prototype](../prototypes/delivery-glance-core-flow-logic-prototype.html)

## In scope

Introduce `dispatch`; extend `delivery` only through its small application interface.

- Eligible means On Duty, usable Current Location measured within two minutes, and no Active Delivery (`ASSIGNED` or `IN_TRANSIT`).
- Rank eligible snapshots by Haversine distance to pickup. Return at most three; use a documented stable Courier ID tie-break and show calculation time plus derived distance, never coordinates.
- `GET /api/deliveries/{id}/courier-recommendations` is Dispatcher-only and returns a fresh calculation, not a durable recommendation record.
- `POST /api/deliveries/{id}/assignment` takes Courier ID, expected Delivery version and command ID. Inside one defined critical section/transaction, revalidate current eligibility, lock durable rows in stable order, insert Assignment and transition to `ASSIGNED`.
- PostgreSQL partial unique indexes are the final arbiter for one active Assignment per Delivery and per Courier. A losing race returns a stable conflict and no partial transition.
- Courier-only guarded commands transition their own Delivery `ASSIGNED → IN_TRANSIT → DELIVERED` and record Delivery Transitions. Delivered ends the active Assignment and withdraws Recipient-facing location. Extend Cancel so a Dispatcher may cancel from `ASSIGNED` before pickup and end that Assignment atomically.

Update the focused Dispatcher detail with calculated candidates and a Direct Assign action. Update the Courier home with the current Delivery plus Confirm pickup and Confirm handoff. Refetch after commands; no realtime internal fan-out is needed.

## Acceptance criteria

- Unit tests prove Haversine ordering, fewer-than-three results, deterministic ties, stale/off-duty/busy exclusion and no Service Zone filtering.
- Integration tests prove recommendation-time state is revalidated at assignment time.
- A Testcontainers concurrency test starts simultaneous Assignments and proves exactly one active Assignment for one Courier and exactly one for one Delivery, with consistent Delivery state and transition history.
- Authorization and ownership tests prevent a Courier progressing another Courier's Delivery and prevent a Dispatcher using Courier actions.
- Lifecycle tests cover the happy path, pre-pickup Cancel and every rejected transition; Handoff Confirmation is explicit and never inferred from GPS proximity.
- The cumulative Compose demo carries one fictional Delivery from Awaiting Courier to Delivered.

## Non-goals

- Courier invitation/acceptance, Matching Round, Match Interest, first-click claiming, timeout/cooldown or recommendation snapshot audit.
- Override reasons, Service Zones, Reassignment, Withdrawal, Revocation or Undeliverable.
- ETA, navigation, route history, Redis/PostGIS or internal SSE.

## PR evidence

Include the concurrency-test result and a short cross-role demo. After merge, run the Sprint 2 release gate and promote only DG-024.
