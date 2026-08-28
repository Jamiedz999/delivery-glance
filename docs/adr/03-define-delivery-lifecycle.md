# ADR 03 — A Delivery has five states and no free-form status

## The question

What states can a Delivery be in, who is allowed to move it between them, and what does the Recipient
see at each one?

## What we decided

Five states. Nothing else exists, and there is no editable status field.

| State | Meaning |
|---|---|
| `AWAITING_COURIER` | Created, no Courier yet. Every Delivery starts here. |
| `ASSIGNED` | A Courier is responsible but has not collected the item. |
| `IN_TRANSIT` | The Courier has the item. |
| `DELIVERED` | Finished — the Courier confirmed the handoff. |
| `CANCELLED` | Finished — a Dispatcher stopped it before pickup. |

Only these moves are allowed:

| From | Who | To |
|---|---|---|
| — | Dispatcher creates a Delivery | `AWAITING_COURIER` |
| `AWAITING_COURIER` | Dispatcher assigns a Courier | `ASSIGNED` |
| `AWAITING_COURIER` | Dispatcher cancels | `CANCELLED` |
| `ASSIGNED` | Dispatcher cancels | `CANCELLED` |
| `ASSIGNED` | Courier confirms pickup | `IN_TRANSIT` |
| `IN_TRANSIT` | Courier confirms handoff | `DELIVERED` |

`DELIVERED` and `CANCELLED` are final. Nothing reopens them; a second attempt is a new Delivery.
After pickup there is no cancel and no way to hand the Delivery to somebody else.

Every successful move appends a Status Change row holding the old state, the new state, who did it
and when. The current state is also stored directly — this is history, not event sourcing.

The Recipient sees plain wording, never the internal name:

| State | What the Recipient reads |
|---|---|
| `AWAITING_COURIER` | "We're preparing your delivery" |
| `ASSIGNED` | "A courier has been assigned" |
| `IN_TRANSIT` | "Your delivery is on the way" |
| `DELIVERED` | "Delivered" |
| `CANCELLED` | "This delivery was cancelled" |

## Why

A fixed list of states with a fixed list of moves is checkable. A free-text status field is not: it
drifts, it gets typos, and no test can say what it should hold.

`PICKED_UP` is not a state — it is the event that starts `IN_TRANSIT`. `FAILED` is not a state either;
it mixes up "this delivery did not work out" with "the system broke".

A stale Courier location does not change the Delivery state. Where the Courier is and how the
Delivery is going are two separate facts, and mixing them would make a network problem look like a
delivery problem.

## What is built

All five states and all six moves, in `DeliveryState.java`. Invalid moves are rejected by
`canTransitionTo`. Status Change rows are written on every move.

An `UNDELIVERABLE` state, and the ability to hand a Delivery back before pickup, were designed and
are not built — see [#29](https://github.com/Jamiedz999/delivery-glance/issues/29).
