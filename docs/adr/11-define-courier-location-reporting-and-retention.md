# ADR 11 — There is nowhere to write a Location History

## The question

When may the product collect a Courier's position, what does it do when it cannot, and where does
that position live?

## What we decided

**On Duty, having a Delivery, and Location Sharing are three separate facts.** Going On Duty asks the
browser for nothing. Being assigned a Delivery starts no tracking. Only the Courier pressing Start
begins Location Sharing, and only while the page is in front of them.

The Courier workspace shows four honest states:

| State | Meaning |
|---|---|
| `OFF` | No session. Nothing is collected. |
| `STARTING` | Start was pressed; waiting on permission or the first fix. |
| `REPORTING` | The page can get positions and send them. |
| `INTERRUPTED` | Still wanted, but the page is hidden, the network is gone, or there is no fix. |

Stop, sign-out, closing the page and reloading all end the session. A fresh page always starts at
`OFF` and needs another Start. Stopping never changes the Delivery.

While `REPORTING` the page sends the newest reading about every ten seconds. There is no offline
queue: after a gap it sends **only the newest position**, so recovery cannot upload a burst that
recreates a route.

A report replaces the Current Location only if all of these hold:

- accuracy is 100 metres or better;
- its measurement time is newer than the stored one;
- the measurement time is not more than thirty seconds in the future, and the report is not already
  over two minutes old;
- the sharing session is still valid.

Ordering is by **measurement time**, never by arrival order. A late report of an older position is a
harmless no-op.

**At most one position exists per Courier, in memory, and it is overwritten rather than appended.**
It is deleted at the earliest of: Stop or permission withdrawal; no reason left to collect; two
minutes after measurement with no replacement; or the session being invalidated. After deletion the
product keeps only the last-success time, the accuracy and the interruption reason — no coordinates.

Coordinates never enter logs, traces, metric labels, backups or any audit record.

## Why

The cheapest way to keep a promise never to build a Location History is to have nowhere to break it.
A table would only need one careless `INSERT` to become a trail; a single overwritten value in memory
cannot become one at all. This is the whole design, and everything else follows from it.

Accepting a position by measurement time rather than arrival time is what stops a delayed upload from
looking fresh. A bad device clock would otherwise be enough to keep a Courier assignable, or to show
a Recipient a marker that is minutes out of date.

The four states exist because "not reporting" has several causes and the Courier deserves to know
which. A page that says `REPORTING` while the phone has no fix is lying to the one person who could
fix it.

The visible cost is deliberate: restart the application and the Courier is still On Duty — that is
durable — but their location is Unavailable until they report again.

## What is built

All of it. `LocationPrivacyTest` reads **every column in the schema** and asserts there is nowhere a
coordinate could be written. A second check asserts the captured application log holds no coordinate
after a real report.

`LatestLocationStore` was expected to need an interface with two implementations. It does not — a
test moves the injected `Clock` by hand — so it is one class with no interface in front of it.
