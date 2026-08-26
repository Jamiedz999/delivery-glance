# Define and name a recipient-first delivery tracking product

Label: wayfinder:map

## Destination

Produce a build-ready, collision-checked recipient-first delivery tracker and a resume-first implementation plan: a deployable end-to-end MVP after three short Sprints and an evidence-backed Core after four, while preserving the fuller product design as explicit Future Work rather than a six-week commitment.

## Notes

- Domain language lives in [CONTEXT.md](../../CONTEXT.md); every session should use the `grilling` and `domain-modeling` skills for human decisions.
- This map is the authoritative scope index across `docs/adr/` and the [Later Backlog milestone](https://github.com/Jamiedz999/delivery-glance/milestone/5). Product decisions are resolved; the implementation queue lives entirely in GitHub Issues.
- Readiness lives on GitHub, not here. This map records dependency order; the `ready`/`blocked` labels and open/closed state on the Issues are what say where execution actually is.
- The user has delegated all remaining ticket decisions to the agent's recommended option; proceed without asking choice questions unless completion requires genuinely new authority or unavailable input.
- Core answers a Recipient's questions: what stage is my Delivery in, where is it, and what happens next? “When should it arrive?” returns only with Future Work 13.
- A Delivery Team creates a Delivery; its Recipient opens an expiring Tracking Link without an account.
- In Core, a Dispatcher directly assigns one of the three nearest Eligible Couriers after atomic revalidation. The consent-based Matching Round remains a fully designed Future Work increment.
- Work proceeds in one-week, ten-to-twelve-hour Sprints that each end with a deployed, demonstrable increment. MVP targets Sprint 3 (about 28–34 focused hours); resume-ready Core targets Sprint 4 (about 38–46 hours). There is no precommitted fifth or sixth week.
- Core uses Direct Assignment from the nearest Eligible Courier recommendation. The richer consent-based Matching Round and other removed branches remain documented Future Work.
- Prefer a direct English name made from two familiar words. Exact name reuse is acceptable when existing projects differ materially in both function or language/technology; avoid a same-name project with substantially the same function and stack because it could look derivative. Avoid names implying fleet management, route planning, or broad logistics.

## Decisions so far

These are resolved records in [`docs/adr/`](../adr/), plus the research and prototype work that fed them. None of them is a ticket; do not open them as GitHub Issues.

- [Choose the public project name](../adr/02-choose-public-project-name.md) — Delivery Glance is the shared name, paired with “Real-time delivery tracking for the last mile.”
- [Define the Delivery lifecycle](../adr/03-define-delivery-lifecycle.md) — Preserves the full six-state design; Core implements only Awaiting Courier, Assigned, In Transit, Delivered, and pre-pickup Cancelled.
- [Define Courier recommendation and assignment](../adr/04-define-courier-recommendation-and-assignment.md) — Preserves the complete consent-based matching design; Core keeps nearest-three eligibility and atomic Direct Assignment, while Matching Round behaviour is Future Work 17.
- [Define the Recipient tracking promise](../adr/05-define-recipient-tracking-promise.md) — Preserves the full public promise; Core keeps state, next step, current-location privacy and freshness, while ETA and detailed timeline behaviour are deferred.
- [Define the Tracking Link lifecycle](../adr/06-define-tracking-link-lifecycle.md) — Preserves the complete link lifecycle; Core implements secure creation, repeatable Copy and automatic Expiry, while recovery controls are Future Work 14.
- [Set the original Core and expansion-stage boundaries](../adr/07-set-core-and-expansion-boundaries.md) — Preserved as the complete-product baseline; its six-week bundle is superseded for implementation sequencing by the resume-ready rescope.
- [Choose the Core technical architecture](../adr/10-choose-core-technical-architecture.md) — Locks the same-origin Java/Spring/PostgreSQL/React modular monolith; Ticket 12 retains the simple foundation, latest-only location and Recipient SSE while deferring provider and scale substitutions.
- [Define Courier location reporting and retention](../adr/11-define-courier-location-reporting-and-retention.md) — Courier-authorized foreground sessions report about every ten seconds, preserve only one ephemeral usable position, degrade honestly through interruption, and never create durable raw Route History.
- [Rescope Delivery Glance to a resume-ready Core](../adr/12-rescope-to-resume-ready-core.md) — Four independently deployable Sprints retain atomic Direct Assignment, latest-only location, secure Tracking Links and Recipient SSE, deliver MVP in Sprint 3 and Core in Sprint 4, and move the rest into explicit Future Work.

## Core implementation queue

The queue lives entirely in [GitHub Issues](https://github.com/Jamiedz999/delivery-glance/issues) — each Issue **is** its specification, in full. Nothing under `docs/planning/` duplicates one.

[Technical baseline](implementation/TECHNICAL-BASELINE.md) is the single source for stack, repository shape and module boundaries. [Issue workflow](implementation/ISSUE-WORKFLOW.md) defines when an Issue may receive the `ready` label. [Incidental findings](implementation/INCIDENTAL-FINDINGS.md) collects real problems spotted while implementing something else; it is a note pad, never a queue. The dependency chain below is fixed and is wired as real GitHub dependency edges; which link is currently live is a question for GitHub.

- [#1 · Scaffold the full-stack walking skeleton](https://github.com/Jamiedz999/delivery-glance/issues/1) — prove React, Spring Boot, PostgreSQL, Flyway, Docker and CI work together without adding business logic.
- *after #1* — [#3 · Add Internal Account sign-in and the persisted Delivery slice](https://github.com/Jamiedz999/delivery-glance/issues/3) — finish the Sprint 1 vertical slice.
- *after #3* — [#6 · Add Courier duty and latest-only Location Sharing](https://github.com/Jamiedz999/delivery-glance/issues/6) — implement the first half of Sprint 2.
- *after #6* — [#7 · Add nearest recommendation and atomic Direct Assignment](https://github.com/Jamiedz999/delivery-glance/issues/7) — finish the internal workflow and Sprint 2.
- *after #7* — [#8 · Add the secure Core Tracking Link](https://github.com/Jamiedz999/delivery-glance/issues/8) — implement creation, Copy, bootstrap and Expiry.
- *after #8* — [#9 · Build the Core Recipient tracking view](https://github.com/Jamiedz999/delivery-glance/issues/9) — add the mobile state/next-step/map/freshness surface.
- *after #9* — [#10 · Add Recipient SSE refresh and reconnect](https://github.com/Jamiedz999/delivery-glance/issues/10) — complete the deployed end-to-end Sprint 3 MVP.
- *after #10* — [#11 · Harden the Core with risk-based evidence](https://github.com/Jamiedz999/delivery-glance/issues/11) — cover concurrency, privacy, degradation, accessibility and E2E risks.
- *after #11* — [#12 · Package the portfolio release](https://github.com/Jamiedz999/delivery-glance/issues/12) — make the Sprint 4 Core reproducible and resume-ready.
## Future work

These are intentionally not Core commitments. They live in [GitHub Issues](https://github.com/Jamiedz999/delivery-glance/milestone/5) under the Later Backlog milestone.

- #27 — Add external travel-time ETA: restore rounded ETA Windows, stale fallback and late handling behind a provider contract.
- #28 — Add Tracking Link recovery and audit controls: add Rotation, Revocation, Reissue, immediate invalidation and reasoned history.
- #29 — Add Delivery exceptions and pre-pickup Reassignment: add Withdrawal, Revocation and Undeliverable without post-pickup transfer.
- #30 — Add Service Zones and explainable recommendation overrides: add polygon eligibility and reasoned shortlist changes.
- #31 — Add the ranked multi-Courier Matching Round: replace Direct Assignment with the designed consent-based top-three, sixty-second selection flow.
- #32 — Run a measured scale and resilience experiment: profile the simple Core before considering Redis, PostGIS, WebFlux or tracing infrastructure.
- #33 — Evaluate a durable domain-event backbone only when a consumer exists: keep Kafka unscheduled until a real non-coordinate replay consumer and outbox design exist.

## Not yet specified

- No build-blocking Core product or architecture decision remains. Repository URL, deployment target/secrets, public hostname and production tile-provider details are environment inputs, not reasons to block Issues 20–27; they are required only for the relevant release gate. Future Work deliberately remains open and is not part of Core Done.

## Out of scope

- Route planning, navigation, and multi-stop optimisation.
- Payments.
- Native mobile applications.
- Recipient rescheduling, address changes, cancellation, or chat.
- Recipient or operator Route History interfaces and historical route animations.
- Machine-learning ETA.
- Multi-tenant SaaS, billing, and subscriptions.
- SLA dashboards, geographic heatmaps, Courier performance analytics, and other operations reporting.
- Bulk Delivery imports, exports, edits, and other bulk operations.
- Self-service registration, password recovery, Delivery Team membership administration, and Courier profile management.
- Email, SMS, Web Push, and operating-system notifications.
- PIN-gated Recipient tracking, proof-of-delivery artifacts, and localisation.
