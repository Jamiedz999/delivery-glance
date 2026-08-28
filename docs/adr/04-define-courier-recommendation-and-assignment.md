# ADR 04 — The Dispatcher assigns; the database decides the race

## The question

Which Couriers can take a Delivery, in what order should the Dispatcher see them, and what stops two
Dispatchers assigning the same Courier at the same moment?

## What we decided

A Courier is available for a Delivery when all three are true:

- they are On Duty;
- their last reported position is under two minutes old;
- they have no Active Delivery.

These are hard conditions. Ranking cannot soften them and neither can a Dispatcher. Vehicle type,
ratings and past performance are not inputs.

The Dispatcher sees the **three nearest** Available Couriers, or all of them if there are fewer than
three, ordered by straight-line distance from their last position to the Pickup Address. Ties break
on longest idle time, then on Courier id, so the order is always the same for the same inputs. The
Dispatcher sees a **distance**, never a coordinate.

The Dispatcher picks one. The database — not the application — decides who wins: two partial unique
indexes mean one caller gets `204` and the other gets `409`.

When nobody is available the Delivery stays `AWAITING_COURIER` and the Dispatcher is told how many
Couriers were excluded and why. The system never quietly relaxes a condition.

## Why

An application-level check cannot win this race by construction. Two Dispatchers reading at the same
instant both see a free Courier, and both proceed. The only thing that can settle it is a constraint
the database enforces at write time.

Ranking by distance is explainable: the Dispatcher can see why a Courier is first. A quality score
built from ratings and history would be neither explainable nor testable at this size.

## What is built

Eligibility, distance ranking, the three-Courier shortlist and atomic assignment.
`AssignmentConcurrencyTest` releases two assignments from a latch against a real PostgreSQL container,
three times per run, and asserts exactly one wins.

A consent-based flow where several Couriers are invited and one is selected sixty seconds later was
designed in full and is not built — see
[#31](https://github.com/Jamiedz999/delivery-glance/issues/31). Service Zones and reasoned overrides
of the shortlist are [#30](https://github.com/Jamiedz999/delivery-glance/issues/30).
