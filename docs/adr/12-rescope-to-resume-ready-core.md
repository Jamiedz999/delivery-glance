# Rescope Delivery Glance to a resume-ready Core

Type: grilling
Status: resolved
Blocked by: 07, 10

## Question

How should the fully specified Delivery Glance product be reduced into independently deployable weekly increments, so that the first useful MVP and a credible resume-ready Core arrive before optional business complexity or scale infrastructure?

## Answer

### Decision and precedence

The existing tickets remain the detailed **full-product direction**, but they are no longer one six-week delivery commitment. This ticket supersedes their Core/Expansion sequencing wherever they conflict.

Delivery Glance will be built through four one-week, ten-to-twelve-hour Sprints. Every Sprint ends in a deployed, demonstrable increment. The first end-to-end MVP is due after Sprint 3 at approximately 28–34 focused hours; the resume-ready Portfolio Core is due after Sprint 4 at approximately 38–46 focused hours. Calendar duration may shrink if more weekly time is available, but the effort and Done criteria do not change.

There is no precommitted fifth or sixth week. Everything beyond Portfolio Core returns to one ordered Later Backlog and is pulled one item at a time only after the Core is deployed and documented.

### What Portfolio Core proves

The project is a recipient-first live delivery tracker, not a miniature marketplace or fleet-management suite. Its portfolio story is deliberately narrow:

> A Dispatcher creates a Delivery and atomically assigns a nearby available Courier; the Courier shares only a foreground Current Location and progresses pickup to handoff; a Recipient follows state and location through a secure no-account Tracking Link with automatic updates.

That story demonstrates the valuable engineering parts without needing dozens of business branches:

- explicit lifecycle transitions rather than free-form status;
- PostgreSQL constraints and a real concurrency test preventing double Assignment;
- Haversine proximity ranking from latest usable positions;
- browser Geolocation with explicit consent, freshness and no Route History;
- a high-entropy, expiring Tracking Link that does not persist its raw capability;
- a mobile Recipient map refreshed by SSE; and
- one repeatable Docker/CI deployment with tests and a documented demo.

### Simplified Portfolio Core behaviour

#### Delivery and Assignment

Portfolio Core implements only:

```text
AWAITING_COURIER → ASSIGNED → IN_TRANSIT → DELIVERED
        └───────────────────────────────→ CANCELLED (before pickup only)
```

The Dispatcher sees the three nearest currently Eligible Couriers and directly assigns one. Eligibility is limited to On Duty, a usable location no more than two minutes old, and no Active Delivery. Core has no Service Zone polygon.

Assignment is not an invitation and needs no Courier response. The assignment transaction revalidates eligibility and relies on unique partial indexes for one Active Delivery per Courier and one active Assignment per Delivery. This retains the concurrency story while deleting Matching Round timers, simultaneous invitations, Match Interest, ranking-at-close, Decline, Timeout, cooldown, override reasons and Recommendation Decision snapshots.

The Courier can confirm pickup and Delivered. Portfolio Core has no Reassignment, Courier Withdrawal, Dispatcher Revocation, Undeliverable outcome or structured reason catalog. Those cases are shown as known limits rather than implemented incompletely.

#### Current Location

The Courier explicitly goes On Duty and separately starts foreground Location Sharing. The browser targets one newest report about every ten seconds while visible. The server holds only one in-memory usable snapshot per Courier; it rejects poor, stale, duplicate and out-of-order readings and deletes the snapshot after two minutes or explicit Stop. Nothing creates a Route History.

Recipient presentation retains the agreed simple freshness language: Live through thirty seconds, Delayed through two minutes, then Unavailable with the marker removed. Portfolio Core keeps these privacy and honesty rules because they are central to the project, not optional polish. The full interruption-reason vocabulary and thirty-day Location Sharing audit are deferred.

#### Tracking Link and Recipient view

A Delivery receives one reusable, seven-day Tracking Link. Copy rederives the same 256-bit HMAC capability; only its verifier and derivation metadata persist. The fragment-to-cookie bootstrap, generic unavailable response, no-store/no-referrer headers and raw-token log exclusion remain because a public bearer link without those basics would be a weak portfolio story.

Portfolio Core implements creation, Copy and automatic Expiry only. Rotation, Revocation, Reissue, reason catalogs, link-history UI, guessing alerts and security-event retention are deferred.

The Recipient page shows Delivery Reference, state-derived headline and next step, limited Courier Display Name while Assigned/In Transit, Handoff Address, and—only In Transit—a MapLibre map with the handoff and current Courier marker plus freshness. Delivered shows the actual time and removes Courier/location. Cancelled uses a generic outcome. ETA Window, Running Late, detailed Recipient Timeline and terminal support workflow are deferred.

SSE is used only for the Recipient live view in Portfolio Core. Dispatcher and Courier pages may refetch after their own commands and use modest polling for changes; a shared three-role realtime event system is not required.

### Keep now versus Later Backlog

| Keep in Portfolio Core | Move out of Portfolio Core |
|---|---|
| Dispatcher/Courier pre-provisioned sign-in | account administration and recovery |
| Create, list and inspect a Delivery | fuzzy search, dashboards and bulk operations |
| Four-state happy path plus pre-pickup Cancelled | Undeliverable, Reassignment and structured reason catalogs |
| nearest-three Haversine recommendation | Service Zone polygons and PostGIS |
| atomic direct Assignment with database constraints | sixty-second Matching Rounds, Interest, Decline, Timeout, cooldown and overrides |
| explicit foreground Location Sharing and latest-only memory | detailed interruption audit and any durable raw location stream |
| secure Link create/Copy/Expiry | Rotation, Revocation, Reissue and full link history |
| Recipient status, current-location map and freshness | ETA, Running Late and detailed public timeline |
| Recipient SSE only | Dispatcher/Courier realtime fan-out and WebFlux |
| health, redacted logs and request correlation | dashboards, distributed tracing and failure-drill infrastructure |
| focused domain, database-race and two E2E journeys | global 90% coverage or the 2,000-session Expansion benchmark |
| one Boot app, one PostgreSQL and Docker deployment | Redis, Kafka, microservices and Kubernetes |

Redis and Kafka are therefore not “removed and forgotten”; they have no job in the Portfolio Core. Redis may be evaluated later only if a measured multi-instance/latest-location or SSE fan-out problem appears. Kafka requires a new durable non-coordinate consumer and an outbox; raw Courier locations never belong in it. PostGIS waits for evidence that in-memory Haversine ranking is a bottleneck.

### Four independently deployable Sprints

#### Sprint 1 — Walking skeleton · 8–10 hours

**Outcome:** a Dispatcher can sign in to a live HTTPS deployment, create a Delivery, and reopen its persisted detail.

- Create the Spring Boot/React/PostgreSQL monorepo, Flyway migration and Docker path.
- Add pre-provisioned Dispatcher and Courier accounts with role-protected routes.
- Implement Delivery create/list/detail and guarded `AWAITING_COURIER`/`CANCELLED` state.
- Add health check, redacted structured logging and the first CI build.
- Deploy fictional seed data and record a one-minute Sprint demo.

This is a real vertical slice and deployment proof, not yet an MVP.

#### Sprint 2 — Dispatchable Delivery · 10–12 hours

**Outcome:** the internal team can carry one Delivery from assignment through handoff.

- Add Courier On Duty and explicit Start/Stop Location Sharing.
- Maintain one latest in-memory location with 30-second/two-minute freshness.
- Show the Dispatcher the three nearest Eligible Couriers using Haversine distance.
- Implement direct atomic Assignment, Courier pickup and Delivered.
- Add lifecycle/ranking tests plus a Testcontainers race proving no double Assignment.

This is the first complete internal workflow and already demonstrates the strongest backend invariant, but it lacks the product's recipient-facing value.

#### Sprint 3 — Recipient MVP · 10–12 hours

**Outcome:** a real Recipient can open a link and follow the active Delivery on a phone.

- Add deterministic HMAC Tracking Link creation, Copy, Expiry and fragment exchange.
- Build the selected Status Story mobile page with state, next step and terminal privacy.
- Add MapLibre with handoff/current markers and freshness treatment.
- Connect accepted location changes to one Recipient SSE channel with reconnect snapshot.
- Deploy and run one complete cross-role happy-path walkthrough.

At this point Delivery Glance is a functional **MVP**: deployed, end to end and useful. It may be shown in interviews as work in progress, but it is not yet labelled a finished portfolio project.

#### Sprint 4 — Portfolio Core · 10–12 hours

**Outcome:** the MVP has enough evidence and presentation quality to put on a resume as completed work.

- Add Tracking Link leak/expiry tests, stale/Stop cleanup tests and SSE reconnect coverage.
- Add two Playwright journeys: happy path and location/link degradation.
- Polish mobile/desktop accessibility and honest empty/error/loading states.
- Add deterministic demo reset, fictional accounts and a reproducible walkthrough.
- Finish README, domain/system diagrams, trade-off notes, screenshots and a short demo video.
- Record only measured results; do not invent scale or latency claims.

Portfolio Core is Done only when another person can open the public demo, follow the README and reproduce the story without assistance.

### Resume checkpoint

The honest resume checkpoint is the end of Sprint 4, approximately 38–46 focused hours. A suitable bullet is:

> Built and deployed a recipient-first last-mile tracking application with Spring Boot, React and PostgreSQL, using database-enforced atomic Courier assignment, privacy-minimised foreground geolocation and SSE live updates through secure no-account Tracking Links.

Add a latency, concurrency or test figure only after the repository contains the reproducible test and its result. At the end of Sprint 3, describe it as an active MVP rather than a completed project.

### Ordered Later Backlog

After Portfolio Core, consider only one independently demoable increment at a time:

1. #27 — external travel-time ETA and honest unavailable/late presentation;
2. #28 — Tracking Link Rotation/Revocation/Reissue and recovery audit;
3. #29 — pre-pickup Reassignment and Undeliverable outcomes;
4. #30 — Service Zone polygons and explainable recommendation overrides;
5. #31 — the full top-three, sixty-second Courier Matching Round;
6. #32 — a measured scale and resilience experiment, introducing Redis, PostGIS, WebFlux or observability tooling only for the bottleneck it proves; and
7. #33 — a durable domain-event backbone only after a new non-location consumer makes replay valuable.

This order is a backlog, not a promise. A later item must state its user value, fit one short increment and leave the deployed Core working when omitted.

### Sprint discipline and cut rule

- Every Sprint begins from the last deployed increment and ends with a URL, automated checks and a short demo.
- New Sprint work does not begin while the previous Sprint's acceptance path is broken.
- Backlog refinement may change the next Sprint; it does not silently enlarge the active Sprint.
- If a Sprint exceeds twelve hours, first remove visual decoration, secondary filters and extra seed cases. Never cut atomic Assignment, location privacy, Tracking Link safety, the end-to-end Recipient path or the tests that justify a resume claim.
- The complete detailed tickets remain reference material for Later work, not hidden obligations of Portfolio Core.
