# Set the Core and expansion-stage boundaries

Type: grilling
Status: resolved
Blocked by: 04, 05, 06

## Question

Which behaviours and quality attributes must ship in the independently deployable 3–4 week Core, which portfolio or scale capabilities may enter the 1–2 week expansion stage, and what is cut when the six-week limit is threatened?

## Answer

> **Superseded implementation boundary:** The complete-product scope below is preserved as planning evidence, but [Ticket 12](12-rescope-to-resume-ready-core.md) replaces its six-week delivery bundle with a three-Sprint MVP, four-Sprint resume-ready Core and individually prioritised Future Work.

### Stage contract

Core is a thin but complete product across Dispatcher, Courier, and Recipient, not a backend milestone or technology demo. It has a hard budget of forty-eight hours over three to four weeks at roughly twelve hours per week. It must pass Core Acceptance and stand alone if no further work happens.

Expansion Stage is optional, begins only after Core Acceptance, and has at most twenty-four additional hours over one to two weeks. It adds measurable scale, resilience, performance, and observability evidence without adding product workflows. Total work stops at seventy-two hours or six calendar weeks.

### Core users and access

Core serves one Delivery Team. Dispatchers and Couriers sign in through pre-provisioned Internal Accounts; Recipients use only Tracking Links. Self-registration, password recovery, membership invitations, role administration, and multi-team tenancy are absent.

The product and project language is English. There is no internationalisation framework or second-language content in this scope.

### Core Delivery data

A Delivery contains only:

- a Delivery Reference unique within the Delivery Team;
- an internal readable Pickup Address and geographic point;
- the Recipient-visible Handoff Address and geographic point; and
- the Delivery Team's centrally configured Delivery Team Contact.

Core stores no Recipient phone number or email address, item contents, price, weight, priority, promised-delivery time, or similar commerce data.

### Core product surfaces

The Dispatcher workspace provides Delivery creation, list and detail views, exact or partial Delivery Reference search, lifecycle-state filtering, Delivery Transition history, the entire Courier recommendation and Matching Round behaviour, Cancellation, Reassignment, and Tracking Link Copy, Rotation, Revocation, and Reissue. It has no address editing, bulk import/export, or analytics. A simple current-state count may appear but is expendable polish rather than a requirement.

The Courier workspace provides sign-in, On Duty control, visible Location Sharing state, Match Invitation responses and Interest withdrawal, the current Delivery, explicit pickup, Delivered and Undeliverable outcomes, and Courier Withdrawal. Courier Display Name and Service Zone are pre-provisioned; profiles, zones, task history, ratings, income, and performance views are not editable product surfaces.

Courier Location Sharing uses real browser Geolocation with explicit permission, reporting, and interruption states. The foreground page must remain open; Core promises no background Web or native tracking. While operating normally it targets one position report approximately every ten seconds. A deterministic route simulator exists only for repeatable tests and demonstrations, alongside rather than instead of the real browser flow.

The Recipient experience implements the complete state, timeline, Current/Last Known Location, freshness, ETA Window, connection, late, terminal, privacy, and Tracking Link promises already defined in [Define the Recipient tracking promise](05-define-recipient-tracking-promise.md) and [Define the Tracking Link lifecycle](06-define-tracking-link-lifecycle.md). Core offers only in-page realtime changes: no email, SMS, Web Push, or operating-system notification.

Neither Core nor Expansion exposes Route History through a UI or API. Exact collection triggers and raw-position minimisation and retention remain a separate product decision in [Define Courier location reporting and retention](11-define-courier-location-reporting-and-retention.md).

### Core Acceptance

Core is accepted only when all of the following hold:

- a clean database can be initialised and the product repeatedly deployed to a low-cost HTTPS environment;
- the shared Public Demo contains only fictional accounts and data, identifies itself as shared, and can be reset safely on demand or on a schedule;
- Recipient and Courier flows are mobile-first, the Dispatcher flow is desktop-first and tablet-functional, and critical journeys target WCAG 2.2 AA through keyboard access, visible focus, semantics, and contrast;
- CI automatically runs formatting, static analysis, automated tests, and the build;
- the README documents positioning, local setup, Demo accounts, a reproducible walkthrough, scope and known limits, plus system and domain diagrams;
- health and readiness checks, redacted structured logs, request correlation, business audit, and basic error, latency, and location-update metrics make the running Core diagnosable; and
- GPS denial, interruption, poor accuracy and staleness, realtime disconnect/reconnect, ETA failure, Matching races, duplicate submission, and service restart preserve durable Delivery truth and present an honest degraded or recovery state.

Tests are risk-based rather than driven by the original brief's arbitrary global ninety-percent coverage target. They include domain tests for lifecycle, ranking, freshness, ETA, and link expiry; database/API/authentication/audit integration tests; explicit concurrent Match Selection tests; and four cross-role end-to-end journeys:

1. create → recommend → Match → unique selection → pickup → location/ETA → Delivered;
2. decline/timeout/no winner → new Matching Round → Reassignment → successful rematch;
3. poor/stale GPS, realtime disconnect, and ETA failure → honest degradation and recovery; and
4. Tracking Link Copy, Rotation, Revocation, Reissue, Expiry, and terminal privacy.

The Public Demo has a health check but no uptime SLA. Low-cost-hosting cold starts are acceptable when disclosed.

### Core performance baseline

A repeatable fifteen-minute test simulates one hundred On Duty Couriers, fifty Active Deliveries, one hundred concurrent Recipient tracking sessions, and a ten-second Location Reporting Cadence. At that load:

- accepted location to Recipient rendering has p95 latency at or below two seconds;
- ordinary API latency is p95 at or below five hundred milliseconds; and
- a Matching Round produces its unique selection within one second of closing.

No test may produce a conflicting Assignment, duplicate winner, or lost Delivery state.

### Core cut ladder

At thirty-six hours, schedule risk removes scope in this order:

1. animation and visual decoration beyond usability and accessibility;
2. Dispatcher current-state counts;
3. partial/fuzzy Reference search, retaining exact search and state filtering; and
4. extra seeded scenarios, retaining one complete repeatable scenario.

The complete business loop, security, explicit failure behaviour, automated tests, and repeatable deployment are never cut. If this ladder cannot bring Core within forty-eight hours, the plan is revisited rather than renaming incomplete Core work as Expansion.

### Expansion Stage

Expansion runs a reproducible thirty-minute scenario with two thousand On Duty Couriers, one thousand Active Deliveries, two thousand concurrent tracking sessions, and ten-second reports—an average of two hundred location requests per second. It targets:

- Recipient location propagation p95 at or below two seconds;
- fewer than one percent failed legitimate requests; and
- zero lost Delivery states and zero duplicate winners.

Expansion may add scalable geographic selection, location ingestion and Recipient fan-out, reliable event processing and recovery, distributed tracing, dashboards, alerts, load tooling, and failure drills. Kafka, Redis, PostGIS, or any other additional component enters only when a baseline or recovery experiment identifies a concrete limitation and the component measurably improves a defined result. Meeting the targets with simpler architecture is a valid result.

Completion evidence comprises reproducible load scripts, Core-versus-Expansion results, resource-use data, metrics/traces/alert captures, and at least one service-interruption recovery experiment. If the target is missed, the report retains the honest failed result and bottleneck, but the Expansion is not described as passed and unstable additions stay out of the default deployment. Core remains complete.

### Explicit six-week exclusions

The six-week product does not include SLA dashboards, geographic heatmaps, Courier performance analytics, bulk operations, imports/exports, post-creation address editing, account lifecycle or team administration, email/SMS/push notifications, Courier profile/history/ratings/income, Route History interfaces, PIN-gated tracking, proof-of-delivery artifacts, or localisation. Existing map-level exclusions continue to apply.
