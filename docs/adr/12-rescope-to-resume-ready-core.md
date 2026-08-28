# ADR 12 — A narrow tracker, finished, rather than a wide one, half-built

## The question

The product had been specified in full: consent-based matching, service zones, reassignment,
undeliverable outcomes, link recovery, ETA. How much of it should actually be built?

## What we decided

Build the narrowest thing that is a whole product, and finish it:

> A Dispatcher creates a Delivery and assigns a nearby available Courier. The Courier shares a
> foreground location and confirms pickup and handoff. A Recipient with no account follows all of it
> through a link that expires and updates itself.

Everything else stays designed and unbuilt: the consent-based Matching Round, Service Zones,
reassignment before pickup, the undeliverable outcome. They are open
[GitHub Issues](https://github.com/Jamiedz999/delivery-glance/issues), not missing work.

Some deliberate boundaries that came with it and still hold:

- **One Delivery Team.** No tenants, no team administration.
- **Accounts are created in advance.** No registration, no invitations, no password reset.
- **English only.** No translation framework.

## Why

The engineering worth showing is in the narrow version, and it is the part that is hard: explicit
state changes instead of a free-text status; a database constraint that settles a race an application
check cannot win; a bearer link that is safe to make public; a location promise kept by having
nowhere to break it; a page that tells the truth about its own staleness.

None of that gets better by adding a sixty-second invitation round or polygon zones. Those add
business branches, and business branches are what turn a finished thing into a half-built one.

The cut also had to survive being *seen*. A product with three roles that all work is demonstrable in
six minutes. A product with eight flows where two are stubbed is not demonstrable at all.

## What is built

The whole of it, plus three things added deliberately afterwards once the narrow version was
finished: external travel-time ETA
([#27](https://github.com/Jamiedz999/delivery-glance/issues/27)), Proof of Delivery
([#50](https://github.com/Jamiedz999/delivery-glance/issues/50)) and Delivery Notifications
([#51](https://github.com/Jamiedz999/delivery-glance/issues/51)).

`docs/testing.md` is the risk matrix: every claim this project makes, the command that proves it, and
what it deliberately does not claim.
