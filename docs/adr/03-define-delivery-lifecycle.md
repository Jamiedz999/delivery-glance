# Define the Delivery lifecycle

Type: grilling
Status: resolved
Blocked by: none

## Question

What are the canonical Delivery states, permitted transitions, responsible actor at each transition, and visible Recipient wording—including rejection, cancellation, failed handoff, reassignment, and stale-location scenarios?

## Answer

> **Portfolio Core scope update:** [Ticket 12](../planning/12-rescope-to-resume-ready-core.md) implements only Awaiting Courier → Assigned → In Transit → Delivered plus pre-pickup Cancelled. Reassignment and Undeliverable remain preserved here for [Future Work 15](../planning/future-work/15-add-delivery-exceptions-and-reassignment.md).

### Canonical states

- `AWAITING_COURIER`: the initial state after a Dispatcher submits a valid Delivery. There is no persisted `DRAFT` or generic `CREATED` state.
- `ASSIGNED`: a Courier has been selected from their binding Match Interest but has not picked up the item.
- `IN_TRANSIT`: the assigned Courier has confirmed pickup and carries the item toward handoff.
- `DELIVERED`: terminal; the Courier confirmed successful handoff.
- `CANCELLED`: terminal; the Dispatcher stopped the Delivery before pickup.
- `UNDELIVERABLE`: terminal; after pickup, the Courier could not complete handoff. A later attempt is a new Delivery.

`PickedUp` is a transition fact that begins `IN_TRANSIT`, not a durable current state. `FAILED` is not a lifecycle term because it confuses a business outcome with a system failure.

### Permitted transitions

| Current state | Trigger | Responsible actor | Next state |
|---|---|---|---|
| Nonexistent | Submit a valid Delivery | Dispatcher | `AWAITING_COURIER` |
| `AWAITING_COURIER` | Complete a Matching Round with an eligible winning Match Interest | System, acting on Courier consent | `ASSIGNED` |
| `AWAITING_COURIER` | Cancel before assignment | Dispatcher | `CANCELLED` |
| `ASSIGNED` | Courier withdraws before pickup, or Dispatcher revokes the assignment | Current Courier or Dispatcher | `AWAITING_COURIER` |
| `ASSIGNED` | Cancel before pickup | Dispatcher | `CANCELLED` |
| `ASSIGNED` | Confirm pickup | Current Courier | `IN_TRANSIT` |
| `IN_TRANSIT` | Confirm successful handoff | Current Courier | `DELIVERED` |
| `IN_TRANSIT` | Confirm unsuccessful handoff with a required reason | Current Courier | `UNDELIVERABLE` |

Every transition not listed above is invalid. `DELIVERED`, `CANCELLED`, and `UNDELIVERABLE` have no outbound transitions. Terminal history is never edited or reopened; any later fulfilment attempt is a new Delivery.

A Dispatcher authorizes one sixty-second Matching Round for up to three recommended Eligible Couriers, but neither that action nor a Match Invitation changes the Delivery state. Match Interest, Match Decline, Match Timeout, a withdrawn Match Interest, a cancelled round, and a round with no eligible interested Courier also cause no Delivery transition. At the close of a valid round, the system revalidates and reranks interested Couriers and atomically assigns the highest-ranked Courier who remains eligible; the Courier's still-active Match Interest is binding consent, so no second acceptance is required.

Reassignment is permitted only before pickup. A reasoned Courier Withdrawal or Dispatcher Revocation preserves the same Delivery and its history, clears the current Courier, notifies both parties, and returns the Delivery to `AWAITING_COURIER`; it does not automatically start another Matching Round. The former Courier is suppressed from further invitations for that Delivery unless a Dispatcher restores invitation permission with a reason, but remains On Duty for other Deliveries unless the Courier explicitly changes that condition. After pickup there is no cancellation, reassignment, or transfer flow in v1; the Delivery must end as `DELIVERED` or `UNDELIVERABLE`.

### Outcome reasons

Both terminal actions require a structured reason code where applicable and may carry an optional internal note. Internal notes are never automatically exposed to a Recipient.

- Cancellation reasons: delivery no longer required, invalid delivery details, item unavailable at pickup, or other.
- Undeliverable reasons: Recipient unavailable, Recipient refused handoff, address inaccessible, unsafe to complete, item damaged, or other.

The current Courier may mark a Delivery `UNDELIVERABLE` without Dispatcher approval, but the action requires explicit confirmation and a reason.

A pre-pickup Courier Withdrawal or Dispatcher Revocation also requires a structured reason and may carry an optional internal note; an `OTHER` reason requires a note.

- Courier Withdrawal reasons: no longer available, cannot reach pickup, vehicle issue, or other.
- Dispatcher Revocation reasons: Courier unresponsive, operational change, assignment error, or other.

### Recipient wording

| Internal state | Recipient wording |
|---|---|
| `AWAITING_COURIER` | “We’re preparing your delivery” |
| `ASSIGNED` | “A courier has been assigned” |
| `IN_TRANSIT` | “Your delivery is on the way” |
| `DELIVERED` | “Delivered” |
| `CANCELLED` | “This delivery was cancelled” |
| `UNDELIVERABLE` | “We couldn’t complete this delivery” |

Match Decline, Match Timeout, matching outcomes, and reassignment history are internal and do not replace the Recipient-facing Delivery status.

### Orthogonal conditions and audit

- Location staleness does not change the Delivery state; it is represented by Location Freshness alongside that state.
- Every successful state change appends a Delivery Transition containing the previous and next states, responsible actor, timestamp, and applicable reason. The current state is also stored directly; this history requirement does not imply event sourcing.
