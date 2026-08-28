# ADR 05 — The Recipient page states its own uncertainty

## The question

What does the Recipient see at each Delivery state, where does each value come from, and how does the
page admit that something is stale instead of showing it as fact?

## What we decided

Everything on the page is derived from the current Delivery state. There is no free text.

| State | Headline | What else is shown |
|---|---|---|
| `AWAITING_COURIER` | "We're preparing your delivery" | Nothing about a Courier — no name, map, position or ETA |
| `ASSIGNED` | "A courier has been assigned" | Courier Name and a provisional ETA Window. Still no position |
| `IN_TRANSIT` | "Your delivery is on the way" | Courier Name, map, Location Age, updated ETA Window |
| `DELIVERED` | "Delivered" | The actual handoff time. No marker, no ETA |
| `CANCELLED` | "This delivery was cancelled" | A plain outcome, the time, and the Support Contact |

The map appears **only** while `IN_TRANSIT`. It shows the Courier's Last Known Location with its
accuracy circle and the Delivery Address — no pickup marker, no trail, no route, no directions.

Location Age is judged on the Recipient's own clock, from when the phone measured the position:

- under 30 seconds — Live, shown as current;
- 30 seconds to 2 minutes — Delayed, shown with its age and no animation;
- over 2 minutes, or nothing usable — Unavailable, marker removed, last report time kept.

The page never guesses where the Courier has moved to between reports, and a newly arrived but older
reading never replaces a newer one.

Whether the page is still connected is shown **separately** from Location Age. A reconnecting page
keeps the facts it already has, each with the time it was true.

The ETA Window is a range rounded outward to five minutes, roughly twenty minutes wide while
`ASSIGNED` and ten while `IN_TRANSIT`. If the travel-time service fails, the last window survives for
five minutes labelled with its age, then disappears. Passing the end of the window says "Running
later than expected" rather than quietly sliding the estimate.

## Why

The failure this design is built against is a page that looks live and is not. A marker sitting still
on a map is indistinguishable from a Courier sitting still in traffic, so the page has to say which
one it is — and it has to work that out on the reader's own clock, because a disconnected page cannot
ask the server how old anything is.

Separating connection from freshness matters for the same reason. "We lost the connection" and "we
have not heard from the Courier" are different problems with different fixes, and collapsing them
into one indicator makes both unreadable.

Being near the Delivery Address never marks a Delivery as delivered. Only the Courier's explicit
confirmation does. A geofence would be a guess presented as a fact.

## What is built

All of it, plus the ETA Window ([#27](https://github.com/Jamiedz999/delivery-glance/issues/27),
merged). The degradation journey in `npm --prefix web run e2e` stops the sharing, restarts the
application under a live stream, and asserts the reading ages Live → Delayed → Unavailable on the
phone's own clock.

Two gaps, both real:

- Times render in the **reader's** time zone, not the Delivery Address's. Written up in
  `docs/planning/implementation/INCIDENTAL-FINDINGS.md`.
- Proof of Delivery is captured at handoff ([#50](https://github.com/Jamiedz999/delivery-glance/issues/50)),
  which this ADR originally ruled out. The Recipient is told proof exists and is never shown it — see
  **Proof Privacy** in `CONTEXT.md`.
