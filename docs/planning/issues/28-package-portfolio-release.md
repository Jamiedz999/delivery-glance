# DG-028 · Package the portfolio release

Type: implementation
Sprint: 4
Area: documentation
Blocked by: DG-027; repository URL, deployment target, public hostname and production tile configuration
Estimate: 4–5 focused hours plus owner deployment/video time

## Outcome

Another person can discover, run, understand and reproduce Delivery Glance without assistance; the public deployment and repository support an honest resume claim for the completed Core.

## Read first

- [Ticket 12: resume checkpoint and Core Acceptance](../12-rescope-to-resume-ready-core.md#resume-checkpoint)
- [Core technical baseline](../implementation/TECHNICAL-BASELINE.md)
- [Issue workflow: Sprint release gates](../implementation/ISSUE-WORKFLOW.md#sprint-release-gates)
- [Future Work Issues 13–19](../map.md#future-work)

## In scope

- Add a safe, explicit demo profile/reset path that recreates fictional accounts and Delivery data without being enabled in production by default. It must never require or retain real personal data.
- Rewrite the root README around the product outcome, a short animated/static preview, architecture, local quick start, demo roles, verification commands, privacy model, trade-offs and known limits.
- Add one small system diagram and one sequence diagram for Direct Assignment → Location Sharing → Tracking Link/SSE. Keep diagrams in a text-reviewable format.
- Document why PostgreSQL + in-memory latest location + SSE were selected and why Redis, Kafka, PostGIS, WebFlux and full Matching are Future Work rather than missing Core dependencies.
- Add screenshots for the Dispatcher, Courier and Recipient surfaces and a reproducible three-role demo script. The owner records a short demo video using that script.
- Configure the user-selected HTTPS deployment, restricted public tile configuration, health check and deployment runbook. Verify secrets are supplied outside Git and the production tracking route has the expected headers.
- Tag the exact accepted revision only after the public demo and clean-checkout reproduction pass.

## Acceptance criteria

- A new contributor follows the README from a clean checkout to the healthy Compose application and both E2E journeys without undocumented steps.
- The public HTTPS deployment completes the Sprint 4 three-role demo and survives an application restart with durable Delivery truth and intentionally unavailable location until a fresh report.
- Repository secret/history scan finds no real credential, Tracking token, personal address or raw Courier coordinate.
- README claims match `docs/testing.md`; every number has a committed reproduction command and result.
- README clearly separates shipped Core from linked Future Work 13–19 and does not imply Redis/Kafka or full matching was implemented.
- Screenshots, diagram labels, demo data and resume bullet use the canonical terms in `CONTEXT.md`.
- An uninvolved reviewer can explain the architecture, run the demo and identify the known limits from the repository alone.

## Non-goals

- A marketing site, custom domain purchase, complex CD platform or infrastructure-as-code portfolio.
- Adding Future Work to make the README look larger.
- Invented traffic, latency, coverage or user-impact claims.

## PR and release evidence

Include the public URL, clean-checkout reproduction record, deployment revision, security-header check, screenshots and final demo script. The human-recorded video may follow the merge but is required before the project is described as a finished resume item.
