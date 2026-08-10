# DG-027 · Harden the Core with risk-based evidence

Type: implementation
Sprint: 4
Area: testing, security
Blocked by: DG-026
Estimate: 5–6 focused hours

## Outcome

The MVP's resume-relevant claims are backed by reproducible automated evidence for its actual risks: assignment concurrency, Tracking Link leakage/expiry, location privacy/freshness, SSE recovery and cross-role accessibility.

## Read first

- [Core Acceptance in Ticket 12](../12-rescope-to-resume-ready-core.md#sprint-4--portfolio-core--1012-hours)
- [Implementation Issue workflow](../implementation/ISSUE-WORKFLOW.md#definition-of-done)
- [Core flow logic prototype](../prototypes/delivery-glance-core-flow-logic-prototype.html)

## In scope

- Add two Playwright journeys against the production-like Compose image:
  1. Dispatcher creates and assigns; Courier picks up, shares and delivers; Recipient follows through a Tracking Link.
  2. Location becomes Delayed/Unavailable, sharing Stops, SSE reconnects, and an expired/unavailable link reveals no Delivery data.
- Make clocks, generated references, fictional coordinates and demo accounts deterministic in tests without adding production backdoors.
- Consolidate the strongest existing backend tests into a documented risk matrix: lifecycle guards, simultaneous Assignment, latest-only ordering/cleanup, HMAC/expiry/log redaction, role isolation, Recipient projection and SSE isolation/reconnect.
- Test keyboard path, focus visibility, semantic names, reduced motion, contrast and the target desktop Dispatcher/mobile Courier/Recipient viewports. Fix defects found within existing Core behaviour.
- Ensure error/loading/empty/conflict/reconnecting/map-unavailable states do not leak data or claim success.
- Capture reproducible measured test results only. If a metric is not produced by a committed command, do not put it in project claims.

## Acceptance criteria

- All canonical checks and both Playwright journeys pass from a clean checkout in CI.
- The concurrency test still proves exactly one winner across repeated runs; privacy tests still prove no durable/logged raw coordinate or token.
- Accessibility checks have no serious/critical automated violations on the three main surfaces, and the documented keyboard walkthrough succeeds.
- The degradation journey proves stale marker removal locally even while SSE is disconnected.
- A `docs/testing.md` risk matrix links each portfolio claim to a command/test and states important untested limits.
- No arbitrary global coverage percentage or invented latency/concurrency number is introduced.

## Non-goals

- New product behaviour, visual redesign, exhaustive browser/device matrix or production load test.
- Chaos platform, distributed tracing, Redis/Kafka or scale infrastructure.
- Fixing Future Work gaps by silently implementing them.

## PR evidence

Attach the CI run, Playwright artifacts and risk matrix. After merge, promote DG-028 only when its human-owned repository/deployment inputs are available.
