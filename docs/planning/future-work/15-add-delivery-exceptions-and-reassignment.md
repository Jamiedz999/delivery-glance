# Add Delivery exceptions and pre-pickup Reassignment

Type: future-work
Status: future
Milestone: Later Backlog
Blocked by: Core Acceptance from 12
Source decisions: 03, 04, 05

## Outcome

Handle operational exceptions explicitly after the happy-path lifecycle is proven: a pre-pickup Assignment can end safely, and an In Transit Delivery can finish as Undeliverable.

## Why deferred

These behaviours multiply state transitions, confirmations, reason vocabularies, Recipient projections and test cases. They are important product completeness work, but they do not strengthen the first portfolio proof as much as atomic Assignment and live Recipient tracking.

## Scope

- Add Courier Withdrawal and Dispatcher Revocation only before pickup.
- Return the same Delivery to Awaiting Courier, preserve Assignment history and suppress the former Courier for that Delivery.
- Add `UNDELIVERABLE` after pickup with the agreed structured reasons and optional internal note.
- Keep post-pickup transfer/reassignment forbidden.
- Update internal and Recipient views without exposing private reasons or withdrawn Courier details.

## Acceptance

- Every new transition is guarded, idempotent and audited with actor/time/reason.
- Reassignment cannot create two Active Assignments or automatically start matching.
- Terminal Delivery states remain irreversible.
- Recipient state removes obsolete Courier, location and ETA data at the correct transition.
- Cross-role E2E covers withdrawal, successful reassignment and Undeliverable.

## Not included

Post-pickup Courier transfer, automatic retry, rescheduling, address change, proof of delivery or Recipient cancellation.
