# Choose the Core technical architecture

Type: grilling
Status: resolved
Blocked by: 07, 11

## Question

Which Core architecture—and which optional expansion substitutions—should implement authoritative Delivery state, secure Tracking Links, Current Location ingestion and freshness, geospatial eligibility and ranking, external travel-time ETA, and automatic Recipient updates without exceeding the six-week scope?

## Answer

> **Portfolio Core scope update:** [Ticket 12](12-rescope-to-resume-ready-core.md) keeps the single-app/PostgreSQL/React foundation, atomic Assignment, latest-only location, secure Link bootstrap and Recipient SSE. ETA/provider work and every scale substitution below are Future Work rather than Core obligations.

### Architecture decision

Delivery Glance Core is a same-origin, single-instance modular monolith with one durable database:

```text
Dispatcher / Courier / Recipient browsers
                  │
                  │ HTTPS: JSON commands/queries + SSE refresh signals
                  ▼
       one Spring Boot application and image
       ├── business modules and role-scoped HTTP adapters
       ├── Spring MVC SseEmitter registry
       ├── latest-only in-memory Current Location store
       ├── round/expiry/ETA schedulers
       └── address and travel-time provider adapters
                  │
                  ▼
          one PostgreSQL database
       durable lifecycle, assignment, access and audit truth

Recipient browser ── map tiles only ──► licensed tile CDN
Boot application ── minimal requests ──► geocoding / travel-time APIs
```

The governing rule is: **PostgreSQL decides what happened; process memory holds only where a Courier is now; HTTP changes facts; SSE tells a browser to reread facts.** An in-memory event, browser cache, ETA provider response, or Current Location can never become an alternative source of Delivery or Assignment truth.

This architecture deliberately does not begin with microservices, WebFlux, WebSocket, Redis, PostGIS, Kafka, CQRS, event sourcing, or a durable event bus. Core's measured load is approximately ten location requests per second and one hundred SSE sessions; those components would create more failure and privacy surfaces before a measured need exists. The supporting official-source review is [Core technical architecture research](../research/core-technical-architecture.md).

### Locked Core stack

| Concern | Core choice |
|---|---|
| Backend | Java 25 LTS, Spring Boot 4.1 current patch, Spring MVC, Spring Security, Spring `JdbcClient`, Bean Validation |
| Database | PostgreSQL 18 current patch, HikariCP, Flyway as the sole schema mechanism |
| Sessions | Opaque server-side Spring Security sessions, persisted through Spring Session JDBC; secure cookies and CSRF remain enabled |
| Frontend | React 19.2 current patch, strict TypeScript, Vite 8.1, Node 24 LTS, React Router and TanStack Query |
| Realtime | HTTP writes and queries plus same-origin `EventSource`; Spring MVC `SseEmitter` with a bounded executor |
| Map | `maplibre-gl` bundled with the application; a contract-approved commercial tile CDN, initially MapTiler Cloud for the portfolio demo |
| Address and ETA | Server-side Mapbox Permanent Geocoding and Mapbox Directions reference adapters, behind separate project-owned ports |
| Verification | JUnit, AssertJ, Testcontainers PostgreSQL, WireMock provider contracts, Vitest/Testing Library, Playwright and reproducible load harnesses |
| Operations | Docker Compose, one multi-stage application image, Actuator, Micrometer, redacted JSON logs and health checks |

The repository will be a monorepo with Maven Wrapper for `server/`, an npm lockfile for `web/`, `compose.yaml`, and deployment/runbook files. The production Vite build is packaged into the Boot jar; development may use the Vite proxy, but production has no separate frontend host and no CORS policy to maintain. Browser-history routes are used because the URL fragment belongs to Tracking Link bootstrap and cannot also be a hash router.

`JdbcClient` and explicit PostgreSQL SQL are selected over JPA for this Core. The defining persistence work is guarded state transitions, `FOR UPDATE`, partial unique indexes, conditional inserts and audit snapshots; keeping that SQL visible is both smaller and easier to test than adding an ORM and then bypassing it for the important paths. Flyway owns every table, check, foreign key and index; no `schema.sql` or runtime schema update is permitted.

### Deep module boundaries

One executable does not mean one undifferentiated codebase. Top-level packages are business modules with package-private implementation and a deliberately small application interface:

| Module | Owns | Interface exposed to other modules |
|---|---|---|
| `identityaccess` | Internal Accounts, password hashes, roles and session-facing policy | current actor and authorization checks |
| `courier` | Courier Display Name, On Duty, idle time and pre-provisioned Service Zones | availability commands and coordinate-free Courier facts |
| `delivery` | Delivery data, current lifecycle state, Assignment history and Delivery Transitions | guarded commands and role-specific Delivery queries |
| `matching` | recommendations, overrides, invitations, Interest, cooldown/suppression, round close and Recommendation Decisions | recommendation and Matching Round use cases |
| `location` | Location Sharing Session and the sole Current Location snapshot per Courier | report/stop commands and freshness-filtered current-location queries |
| `eta` | one current ETA Window projection and its failure/freshness state | current ETA query; `TravelTimePort` is private to the module |
| `trackinglink` | link generations, derivation/verifiers, Copy/Rotation/Revocation/Reissue, derived grants and link/security history | Dispatcher link commands and link-holder authorization |
| `recipientview` | the privacy-reduced, state-derived Recipient snapshot | one authorized snapshot query and realtime subscription scope |

Role controllers orchestrate these facades inside a transaction; they never reach another module's repository. Entities, SQL repositories, provider DTOs and controllers are internal. Cross-module durable changes are synchronous and transactional. Small after-commit events such as `DeliveryViewChanged`, `LocationChanged` and `TrackingAccessEnded` are only refresh hints.

`shared` may contain technical identifiers, `Clock`, pagination and error primitives, but no generic business services or repository abstractions. The only preselected provider ports are true external boundaries: address resolution and travel time. The location module may have an internal `LatestLocationStore` interface because it already needs a fake-clock test implementation and has an evidence-defined Redis substitution; consumers still see only the module facade.

One Spring Modulith `ApplicationModules.verify()` architecture test enforces acyclic package access and internal-package boundaries. Core does not enable Modulith's persistent event registry or split the build into multiple artifacts. New repository interfaces, event brokers or service boundaries require an actual second implementation or deployment need rather than being added speculatively.

### Durable model and command consistency

PostgreSQL stores Delivery, Courier, Internal Account, Service Zone, Matching Round, invitation/response/Interest, Assignment, current ETA, Tracking Link generations, derived session grants and the agreed coordinate-free audit. It does not store Current Location or raw position reports.

Pickup and Handoff points are durable Delivery facts, not Courier Route History. They are stored as validated WGS84 longitude/latitude with the readable addresses. Service Zones are pre-provisioned GeoJSON polygons and compiled to JTS geometries. At Core scale, the matching module scans at most one hundred current snapshots, tests point-in-polygon with JTS, and ranks eligible Couriers with Haversine distance; it records only the derived distance and rationale. The confirmed Handoff point is also mapped to a configured IANA time-zone polygon and that `ZoneId` is stored with the Delivery, so Recipient times never depend on the browser zone.

Every unsafe business request carries a client-generated command ID and, where it changes an existing aggregate, an expected version. A unique command receipt or transition key makes a retry return its original business result. The receipt stores only an outcome code and internal result reference, never a response body, raw Tracking Link or coordinate; `Copy Tracking Link` safely rederives its response. Conditional updates require the expected current state and version; a zero-row update is a conflict, never silent success. Location reports use their own ephemeral idempotency/generation check and never enter the durable command table with a coordinate payload.

Core uses PostgreSQL `READ COMMITTED` with explicit short row locks, stable lock ordering and constraints; it does not enable `SERIALIZABLE` globally. A transaction that encounters a deadlock, serialization failure or unique-conflict path is rolled back completely before a bounded retry. Business audit is written in the same transaction as the result it explains. In-process notification occurs only after commit and may safely be lost because every view can reconstruct current truth.

The database invariants include at least:

```sql
CREATE UNIQUE INDEX uq_open_round_delivery
    ON matching_round(delivery_id) WHERE closed_at IS NULL;

CREATE UNIQUE INDEX uq_active_interest_courier
    ON match_interest(courier_id) WHERE released_at IS NULL;

CREATE UNIQUE INDEX uq_active_assignment_delivery
    ON assignment(delivery_id) WHERE ended_at IS NULL;

CREATE UNIQUE INDEX uq_active_assignment_courier
    ON assignment(courier_id) WHERE ended_at IS NULL;
```

Equivalent checks enforce one current Tracking Link generation per Delivery and a unique link verifier. Delivery Reference is unique inside the single Delivery Team. Application checks improve error messages; database constraints remain the final arbiter.

### Atomic Matching Selection

A persisted `closesAt` makes the sixty-second round independent of a browser timer. A frequent scheduled worker claims overdue open rounds; on startup it immediately catches up every overdue round. Closing is idempotent and follows one transaction boundary:

1. Acquire the interested Couriers' in-process keyed locks in stable Courier-ID order, then lock the Matching Round, Delivery and the same Courier rows with `FOR UPDATE` in stable order.
2. Return the existing result if the round is already closed or the Delivery is no longer `AWAITING_COURIER`.
3. Revalidate On Duty, the current Location Sharing generation, freshness/accuracy, Service Zone coverage and absence of an Active Delivery.
4. Rerank only the still-interested Eligible Couriers with the locked policy and try them in order.
5. Insert the Assignment under the two partial unique indexes, transition the Delivery to `ASSIGNED`, release every Interest, close the round and save the complete coordinate-free Recommendation Decision in one commit.
6. If a candidate loses a constraint race, use a clean transaction retry and continue with the next valid candidate; never report two winners or leave a half-closed round.

Location report, explicit Stop and Match Selection use the same per-Courier keyed lock whenever they touch both a durable sharing generation and the in-memory snapshot. The global order is Courier locks, then database rows. This gives concurrent Stop-versus-Selection a defined order without persisting coordinates: either Selection linearizes first and a later Stop honestly removes location from an already Assigned Delivery, or Stop linearizes first and that Courier is ineligible.

### Current Location ingestion and deletion

The Courier client calls browser `watchPosition()` only after explicit Start and only while the page is visible. It retains only the newest browser reading, targets a send about every ten seconds, pauses when hidden, sends no backlog on recovery and never uses a service worker to imply background tracking.

Starting creates a random page-held sharing secret and a monotonically increasing server generation. PostgreSQL stores only the secret verifier and the permitted coordinate-free Location Sharing Session audit. Reload loses the page secret and therefore returns to `OFF`; the same still-open page can recover after network or service interruption. A database-backed Internal Account session lets a still-open page authenticate after an application restart, but only a newly submitted valid fix recreates Current Location.

The Core `LatestLocationStore` is a `ConcurrentHashMap<CourierId, Snapshot>`. One atomic `compute` operation validates and replaces the complete value; it never mutates individual coordinate fields. A snapshot contains only longitude, latitude, accuracy, `recordedAt`, `receivedAt`, sharing-session identity and generation.

- Coordinates, timestamp bounds and session purpose are validated before entry to the store.
- Accuracy poorer than 100 metres, a reading older than two minutes on receipt, a future reading more than thirty seconds ahead, an older `recordedAt`, or a duplicate is rejected without refreshing freshness.
- Equal `recordedAt` replaces only when accuracy is better.
- Reads apply the two-minute freshness boundary themselves. A generation-guarded expiry task removes the value at that boundary; cleanup timing can never make stale data usable.
- Every role derives the 30-second and two-minute presentation boundaries from `recordedAt`; the Recipient client also runs its own visible countdown, so a missed event or disconnected page still removes an expired marker no later than its local two-minute boundary.
- Stop, permission withdrawal, sign-out and loss of all collection purpose call the same idempotent `removeIfGenerationMatches` immediately. A terminal transition always removes Recipient location and ETA; it removes the shared Current Location itself only when the Courier is also Off Duty and therefore has no remaining matching purpose.
- The request body is discarded after validation/update and is excluded from access logs, exception capture, metrics, traces, backups and audit.

An application restart intentionally empties this store. Delivery and Assignment remain authoritative; Recipient and Dispatcher show Unavailable until the still-open Courier page reports one fresh fix or the Courier explicitly starts a new session. There is no recovery replay, durable coordinate queue or hidden LocationPing table.

### Recipient Tracking Link and session design

Each link generation has a random 128-bit internal identifier, an integer generation and a key version. A deployment key ring contains at least one 256-bit secret outside the database, image and repository. The same current raw token is reproducible without storing it:

```text
input = lengthPrefixed(
  "delivery-glance/tracking-capability/v1",
  keyVersion,
  randomInternalLinkId,
  generation
)

rawToken = HMAC-SHA-256(keyRing[keyVersion], input)  // full 32 bytes
urlToken = base64urlWithoutPadding(rawToken)         // 43 characters
verifier = SHA-256(rawToken)
```

Only `linkId`, `deliveryId`, generation, key version, the unique verifier, issue/effective-expiry/status fields and audit metadata persist. Effective expiry is always the earlier of seven days from issue or twenty-four hours after a terminal transition. `Copy Tracking Link` rederives the token, checks its digest against the stored verifier and returns the same `/track#t=...` URL under `Cache-Control: no-store`; request/response bodies are never logged. Rotation commits a new generation and verifier while preserving the replaced generation's absolute expiry; Reissue is non-terminal only and creates a new issue time and seven-day ceiling. Both invalidate the previous generation in the same transaction. Planned key rotation retains an old key only while it has active links; suspected key compromise batch-revokes/reissues affected links rather than preserving them.

`GET` and `HEAD /track` serve only generic no-data HTML and a small first-party bootstrap script; they never look up, activate, consume or extend a link. The fragment never reaches the server. Before loading the map or main tracking bundle, the script:

1. reads and strictly decodes the 43-character token;
2. sends it once in a same-origin JSON `POST` protected by an anonymous CSRF cookie, Origin/Fetch-Metadata checks and bounded rate limiting;
3. immediately removes the fragment with `history.replaceState`, including on failure; and
4. on success, receives only a `Secure`, `HttpOnly`, host-only, `SameSite=Lax` opaque session cookie containing no link data in the browser.

The tracking grant inside the server-side session stores only `linkId`, generation and effective expiry. It has an independent expiry no later than the Tracking Link and viewing never extends it. Internal Account authentication has an eight-hour inactivity timeout. Recipient endpoints never inherit Dispatcher or Courier authority. Every Recipient snapshot request, SSE connect and heartbeat rechecks current generation, status and expiry in PostgreSQL. Rotation/Revocation sends connected sessions a data-free `access-ended` event, makes the client clear sensitive state, and closes those emitters after commit; a missed close cannot authorize the next read or heartbeat.

Unknown, malformed, expired, rotated and revoked tokens take the same indexed/dummy-work failure path and produce the same data-free Unavailable Link View. A valid token is never auto-revoked by guesses. A bounded in-memory token bucket protects all redemption attempts after normalising the client address from the one trusted proxy: the Core defaults are 120 attempts per minute per IPv4 address or IPv6 `/64`, with a burst of 120, and 600 attempts per minute for the instance, with a burst of 600. Limits are configuration, covered by the 100-session acceptance test, and may be tightened from observed traffic without changing link semantics. Counters contain neither token nor Delivery identity. Sustained abuse creates an aggregate security signal rather than an availability attack against a valid link.

Tracking responses send `Cache-Control: no-store`, `Referrer-Policy: no-referrer`, `X-Robots-Tag: noindex, nofollow, noarchive`, `X-Content-Type-Options: nosniff`, `frame-ancestors 'none'`, `base-uri 'none'` and a route-specific CSP. No analytics, session replay, third-party script or service worker runs on the Tracking surface. Tile/style/font hosts are a minimal CSP allowlist loaded only after the fragment is gone; tile URLs contain no token, Delivery ID or marker coordinate.

Tracking Link business history remains with its Delivery. A derived grant becomes unusable no later than its link's effective expiry and its expired session row is cleaned within twenty-four hours. Coordinate-free link security events—first successful establishment per generation, reuse of a known unavailable generation, and aggregate guessing alerts—expire after thirty days; Location Sharing Session audit already has its agreed thirty-day retention. The security record may use a daily rotating keyed source fingerprint for abuse correlation, but never stores a raw token, full request URL, coordinate or Recipient browsing history.

### Authoritative views and SSE

All state-changing actions remain authenticated JSON HTTP commands. Each role has a server-built projection; the frontend never receives an internal object and hides fields with CSS. `recipientview` applies the exact state, freshness, ETA and terminal-privacy table before serialization.

SSE is a lossy update channel over those projections:

- the server registers the emitter, then immediately emits a `refresh-required` event with a non-sensitive process epoch and sequence;
- subsequent after-commit or latest-location changes emit only `refresh-required`, `access-ended` or connection comments, and TanStack Query refetches the authorized snapshot;
- sequence ordering applies only inside one random process epoch; a new epoch after restart forces a refresh and clears any old location before applying the new snapshot. Concurrent HTTP snapshots compare durable Delivery version and `recordedAt`, but neither SSE `id` nor `Last-Event-ID` promises replay;
- every reconnect repeats authorization and an immediate refresh, so a missed event cannot leave durable state permanently stale;
- a comment heartbeat runs about every fifteen seconds, revalidates Tracking grants, and detects dead connections;
- completion, timeout and error callbacks remove emitters; sends use a bounded, instrumented executor rather than Spring MVC's unsuitable default executor.

The reverse proxy disables response buffering and keeps its idle timeout beyond two heartbeats. Existing facts remain timestamped while a normal connection is `Reconnecting`; a connected Rotation/Revocation explicitly clears them. No SSE event contains a raw capability or Courier coordinate, so an authorization race can at most expose that “something changed,” after which the snapshot request is still denied.

### ETA and provider boundaries

`AddressResolutionPort` and `TravelTimePort` expose project-owned value types, not vendor DTOs. Test/demo fixtures implement the same ports deterministically; CI never calls paid services. Production reference adapters use server-held Mapbox credentials:

- address confirmation uses Permanent Geocoding because pickup/handoff strings and points must be retained;
- Assigned ETA requests a route through Courier → pickup → handoff, adds the fixed five-minute pickup buffer and returns the approximately twenty-minute rounded window;
- In Transit ETA requests Current Location → handoff and returns the approximately ten-minute rounded window;
- requests ask only for duration/distance and no route geometry, polyline, steps, Courier ID, Delivery ID or Recipient data.

ETA work runs after the state transaction on a bounded executor, so a provider cannot hold a lifecycle or Assignment lock or break the 500 ms ordinary-command target. A startup/scheduled scan and each accepted current fix recover a missing due calculation; per-Delivery coalescing permits at most one provider call per minute. PostgreSQL upserts one latest ETA projection rather than an ETA history.

Timeout, rate budget and a simple circuit-open/backoff state protect the provider. A failure retains the last successful window for no more than five minutes under its visible age, then removes it. Location Unavailable removes location-derived ETA immediately. Passing the published upper bound persists a `Running Late` presentation fact until the recalculation result is shown as updated; a later estimate never rewrites the missed fact silently.

MapLibre is bundled first-party. The default MapTiler tile requests go directly from the browser because its standard terms ordinarily disallow a transparent server proxy/cache; the provider therefore sees browser IP, request time and approximate viewport tile area. This is disclosed in the privacy/provider record. Attribution is always visible, the token is public-origin-scoped with a cost cap, and exact Courier/Handoff markers are drawn locally rather than sent in tile URLs. Community OSM tiles/Nominatim are not a production fallback because their official policies conflict with this tracking workload and `no-referrer`; Google data is not mixed with MapLibre without a fresh storage/display terms review.

Before implementation, one short provider record must confirm the current plan and contract, permanent-storage right, MapLibre compatibility, attribution, EEA/DPA position, privacy disclosure, key restrictions, quota, billing alert/cap, status page and deletion behaviour. If that gate fails, the ports remain and a licensed alternative is chosen; the code does not silently downgrade to straight-line ETA or an unapproved free endpoint.

### Security, observability and retention

Dispatcher/Courier passwords use Spring Security's delegating password encoder and role checks at both controller and use-case boundaries. Internal and Recipient sessions are database-backed opaque sessions; login changes the session ID, logout invalidates it, cookies are `Secure`, `HttpOnly`, host-only and `SameSite=Lax`, and unsafe requests keep Spring's SPA CSRF protection. Production accepts forwarded client/protocol headers only from the known reverse proxy and serves no permissive CORS configuration.

Actuator provides liveness/readiness and protected metrics. Micrometer records low-cardinality HTTP latency/errors, JDBC pool use, location accepted/rejected by reason, Matching close duration/outcome, SSE connections/send failures, provider latency/failures and ETA availability. Logs are structured JSON with a generated correlation ID and enumerated result/reason fields. Coordinates, addresses, free-form notes, raw link/session material and any Delivery/Courier/Link identifiers are forbidden from logs, metric tags and trace attributes; an automated test/log scan enforces the rule.

Core exposes a protected Prometheus-format endpoint for acceptance evidence but does not operate Prometheus, Grafana or an OpenTelemetry Collector solely for one process. Micrometer leaves a clean future export seam. Business audits use their product-defined retention; coordinate-free security evidence and Location Sharing Session audit use thirty days. Database backups contain durable business state but, by construction, no Current Location or raw coordinate report.

### Deployment and restart behaviour

The first production topology is Docker Compose on the existing low-cost ARM64 EC2 host:

```text
Internet → Caddy :443 → delivery-glance app :8080 → PostgreSQL 18
                                  └──────────→ approved providers
```

Caddy provides TLS, HSTS and streaming-friendly proxy settings. The application and PostgreSQL are on a private Compose network; PostgreSQL has no public port. A named volume holds PostgreSQL, and an encrypted nightly `pg_dump` with seven-day retention is uploaded using the EC2 instance role. The application image is multi-stage and multi-architecture; it contains the Vite assets and Java runtime. Secrets come from deployment secret injection/SSM, never an image, Compose file or repository.

Once a GitHub repository exists, GitHub Actions uses pinned actions and OIDC rather than long-lived AWS keys to run formatting, static analysis, backend/frontend tests, the Docker build, migration smoke test and deployment. The fifteen-minute Core load gate is a required release workflow rather than a slow check on every commit. The Public Demo runs only fictional fixtures and uses an operational, non-public reset command that transactionally reseeds durable state and clears sessions, Current Location and emitters.

Spring Session JDBC lets signed-in users and redeemed Tracking grants survive an application restart, while every SSE connection reconnects and rereads its snapshot. Overdue Matching Rounds close from `closesAt`; link expiry is enforced on every access even before its audit scheduler catches up; current ETA may be reused only inside its five-minute rule. Current Location alone is intentionally empty and repopulates only from a new valid foreground report. A database outage fails commands and authorization closed and buffers no coordinates or business writes for later replay.

This is a single-instance, low-cost deployment with disclosed restarts and no uptime SLA. It is not presented as highly available. Repeatability, state integrity and honest recovery are Core requirements; zero-downtime deployment is not.

### Stress and acceptance proof

| Scenario | Required architectural outcome |
|---|---|
| Two rounds or duplicate close requests race for a Courier | keyed/row locks and unique partial indexes yield no more than one active Assignment; retries return one stable result |
| Courier Stop races with Match Selection | the common generation lock gives one order; an old session can never restore eligibility or location |
| Old, poor, duplicated or future GPS arrives | atomic compare rejects it; freshness, ranking and ETA do not move |
| Application restarts mid-round or with open pages | durable state and sessions survive; overdue work catches up; SSE snapshots recover; Current Location is visibly unavailable until a fresh fix |
| ETA/geocoder is slow or unavailable | no state transaction waits on ETA; last ETA ages out honestly; new Delivery address confirmation fails explicitly rather than inventing a point |
| SSE disconnects or drops an event | the browser shows Reconnecting and a reconnect always obtains the current authorized snapshot; there is no replay dependency |
| Link rotates while an old page is open | generation check denies every later snapshot/heartbeat and a connected page receives `access-ended` and clears sensitive state |
| PostgreSQL is unavailable | commands fail without local acceptance, no assignment is invented, and no location backlog is retained |
| Tile provider is unavailable | lifecycle/ETA text remains usable; the map shows an explicit unavailable basemap rather than switching to an unapproved provider |

Verification follows risk, not a global coverage percentage:

1. Plain domain tests cover lifecycle, eligibility/ranking, freshness, ETA windows, public projection and expiry.
2. PostgreSQL 18 Testcontainers runs the real Flyway migrations and covers row locks, every partial index, idempotency and hundreds of barrier-synchronised overlapping-round races; H2 is not used.
3. Provider contract tests use WireMock and deterministic fakes; a sandbox smoke test alone touches Mapbox.
4. Vitest/Testing Library covers state renderings, privacy removal and error states; Playwright covers the four locked cross-role journeys, fragment removal, CSRF/cookies, Geolocation/visibility and SSE reconnect/revocation.
5. A reproducible fifteen-minute release test drives one hundred On Duty Couriers, fifty Active Deliveries and one hundred tracking sessions. It records ordinary API p95, round-close time, accepted-location-to-browser-render p95, errors, executor/DB pool use, memory and leaked connections, and must meet the already locked thresholds with zero conflicting Assignment.

### Expansion substitutions and their triggers

Expansion begins only after Core Acceptance and changes an implementation inside an existing module; it does not add a second product workflow.

| Candidate | Evidence required before introduction | Permitted substitution and hard boundary |
|---|---|---|
| Redis | more than one Boot instance is required, or the 2,000-SSE / 200-location-requests-per-second test proves in-memory latest state or fan-out is the bottleneck | replace the location module's internal store with one latest value per Courier plus TTL and atomic generation/time update; disable RDB/AOF/backups. Pub/Sub carries only invalidation and reconnect reads latest state. No Streams or raw coordinate history |
| PostGIS | `EXPLAIN (ANALYZE, BUFFERS)` and profiling show JTS/Haversine Service Zone/ranking work is the dominant reason the Matching target fails, or polygon count/complexity materially grows | enable it in the same PostgreSQL and move durable point/zone containment and distance filtering behind the matching facade. It does not create a LocationPing table or second truth |
| WebFlux | after tuning the MVC async executor, heartbeat, payload and proxy, the 2,000-connection test still fails specifically because blocking response writes/thread memory dominate, and the relevant I/O can become end-to-end non-blocking | replace only the realtime web adapter while retaining HTTP commands, SSE semantics, PostgreSQL truth, module interfaces and privacy behaviour; do not maintain parallel MVC/WebFlux controllers |
| Mapbox quota change or self-hosted OSRM | the 1,000-Delivery ETA load exceeds contracted quota/cost, or a documented privacy/data-residency requirement justifies operating routing data | negotiate/batch where contractually supported or replace `TravelTimePort`; keep cadence, no-polyline storage and unavailable semantics. Straight-line ETA remains forbidden |
| OpenTelemetry backend | the Expansion observability story includes a real collector/backend and a failure question traces can answer | enable the existing Micrometer/OTLP export with low-cardinality, redacted attributes; a collector is not a prerequisite for Core |
| Kafka | new independent services/consumers need durable replay of non-coordinate domain events and a transactional outbox/idempotent-consumer design exists | only then add it for those events. Current Expansion defines no such consumer, so Kafka has no six-week trigger. Raw location is never published because retention/replay would violate the no-Route-History decision |

Redis, PostGIS and WebFlux are not a bundle: each needs its own failed baseline, one-variable experiment and before/after evidence. A simpler Core that passes the Expansion load remains the preferred default. Every accepted substitution gets a short ADR with the measured bottleneck, data-retention effect, rollback path and results.

### Build order and scope defence

Implementation proceeds by risk, not by visual layer:

1. establish the Boot/React same-origin build, PostgreSQL/Flyway, session/CSRF, Testcontainers and CI;
2. implement Delivery transitions and database constraints, then prove concurrent Matching Selection;
3. implement Tracking Link derivation/exchange/revocation and automated token-leak tests;
4. implement foreground Location Sharing, latest-only storage, expiry and privacy tests;
5. add provider ports, address confirmation, ETA degradation and MapLibre;
6. implement the selected Dispatcher, Courier and Recipient prototypes against authoritative snapshots; and
7. add SSE last, then deployment, the four E2E journeys and the Core load/restart gate.

The existing prototype HTML is a behavioural reference, not production code. The established cut ladder removes decoration, counts, fuzzy search and extra seeds before it removes a complete flow, security, tests or truthful failure behaviour. No architecture component is justified merely because another same-name or similar delivery project used it.
