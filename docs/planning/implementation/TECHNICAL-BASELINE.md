# Delivery Glance Core technical baseline

Status: current
Supersedes for Core implementation: the heavier parts of [Ticket 10](../../adr/10-choose-core-technical-architecture.md)
Scope authority: [Ticket 12](../12-rescope-to-resume-ready-core.md)

## Purpose

This is the single implementation source for the Core stack, repository shape and module seams. Research explains why; implementation Issues say what to build next. An Agent must not infer extra Core dependencies from the complete-product research or prototypes.

## Locked Core stack

| Concern | Choice for Core |
|---|---|
| Backend | Java 25 LTS, Spring Boot 4.1 current patch, Maven Wrapper, Spring MVC, Spring Security, Bean Validation, Spring `JdbcClient` |
| Durable data | PostgreSQL 18 current patch, HikariCP, Flyway only; no JPA/Hibernate and no runtime schema generation |
| Sessions | Opaque same-origin Spring Security sessions; Spring Session JDBC when Internal Accounts arrive; secure cookies and CSRF stay enabled |
| Frontend | Node 24 LTS, React 19.2 current patch, strict TypeScript, Vite 8.1, React Router, TanStack Query |
| Realtime | Normal HTTP commands/queries plus same-origin `EventSource`; Spring MVC `SseEmitter` emits refresh hints, never authoritative state |
| Map | Bundled `maplibre-gl`; production style/tile URL is an environment input and carries no Delivery or Tracking token |
| Verification | JUnit, AssertJ, Testcontainers PostgreSQL, Vitest, Testing Library, Playwright and axe-core, introduced by the Issue that first needs them |
| Packaging | Docker Compose for local PostgreSQL; one multi-stage application image containing the Boot app and compiled React assets |
| Operations | Actuator health, request correlation and redacted logs; no monitoring platform is required for Core |

Versions are pinned in lock/build files by Issue 20. A later patch-version update is allowed only in its own dependency PR with the full verification suite; it does not reopen the architecture decision.

## Repository shape

Issue 20 creates only the files needed for a runnable walking skeleton:

```text
delivery-glance/
├── .github/workflows/ci.yml
├── server/
│   ├── .mvn/wrapper/...
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/deliveryglance/...
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/
│       └── test/java/com/deliveryglance/...
├── web/
│   ├── package.json
│   ├── package-lock.json
│   ├── tsconfig*.json
│   ├── vite.config.ts
│   └── src/...
├── compose.yaml
├── Dockerfile
├── .env.example
└── README.md
```

- Development uses Vite's same-origin-looking `/api` proxy to Boot.
- Production builds the React assets first, places them in the Boot jar, and serves browser-history routes from the same application origin.
- `server/target`, `web/node_modules`, `web/dist`, local environment files and secrets are ignored. Lockfiles and Maven Wrapper files are committed.
- Root scripts are optional. If introduced, they only compose the canonical commands below; they do not create a second build system.

## Deep modules and seams

The backend is one executable with business modules, not a collection of technical `controller/service/repository` layers spanning the whole application.

| Module | Introduced by | Owns | Small interface exposed to peers |
|---|---:|---|---|
| `system` | 20 | health-adjacent build/runtime proof | read-only system status |
| `identityaccess` | 21 | Internal Accounts, roles and session-facing policy | current actor and authorization checks |
| `delivery` | 21 | Delivery data, lifecycle and transitions | guarded commands and role-specific queries |
| `courier` | 22 | Courier identity, display name and On Duty state | availability commands and coordinate-free facts |
| `location` | 22 | sharing intent and the sole latest location snapshot | report/stop commands and freshness-filtered reads |
| `dispatch` | 23 | eligibility, nearest-three recommendation and atomic Direct Assignment | recommend and assign use cases |
| `trackinglink` | 24 | link derivation/verifier, Copy, Expiry and derived grants | Dispatcher link commands and link-holder authorization |
| `recipientview` | 25 | privacy-reduced Recipient projection | one authorized snapshot query and subscription scope |

Implementation details, SQL repositories, web DTOs and provider DTOs stay package-private where Java allows it. Controllers call module interfaces; they do not reach into another module's repository. Test behaviour through the module/API interface. A `shared` package may hold `Clock`, identifiers, error primitives and credential primitives — issuing a random secret, digesting it, comparing in constant time — but never generic business services or speculative repository abstractions. The test for a credential primitive is that it knows nothing about what the secret means: `Secrets` arrived only when a second module needed the same three operations, and the policy about what each secret authorizes and how long it lives stays in the module that owns it.

Create a new seam only when it hides real complexity or has a real second side. For Core:

- `LatestLocationStore` was expected to be a seam justified by two real implementations, production memory and a deterministic fake-clock one. Issue 22 found that a test only has to move the injected `Clock` by hand, so the second implementation never had a job to do. It is one in-memory class with no interface in front of it.
- Map rendering is isolated behind one React component because tests use a local no-network substitute and production uses MapLibre.
- Redis, Kafka, PostGIS, WebFlux, microservices and a generic event bus are not seams or dependencies yet.
- ETA/geocoding provider ports are not created until Future Work 13 is actually pulled.

## Runtime and data rules

- PostgreSQL is authoritative for Internal Accounts, Deliveries, lifecycle transitions, Assignments and Tracking Link metadata.
- Process memory owns only the newest usable Courier location. Restarting the app intentionally makes location Unavailable until a fresh report arrives.
- Raw Courier coordinates never enter PostgreSQL, Kafka, logs, metrics, traces or audit tables.
- HTTP commands change durable facts. SSE carries only scoped invalidation/version hints; every reconnect rereads an authorized snapshot.
- Unsafe authenticated requests use CSRF protection. Internal routes require an Internal Account role. Recipient routes require only the derived Tracking Link grant and never inherit internal authority.
- The frontend receives role-specific DTOs. It never receives a broad domain object and hides sensitive fields with CSS.

## Core build contracts

Issue 20 must establish these commands; later Issues keep them green:

```bash
(cd server && ./mvnw verify)
npm --prefix web ci
npm --prefix web run check
docker compose up --build --wait
curl --fail --silent http://localhost:8080/actuator/health
curl --fail --silent http://localhost:8080/api/system
docker compose down
```

`npm run check` must run TypeScript checking, unit tests once, and a production build. `mvnw verify` must include backend tests and architecture rules that have been introduced so far. CI runs the same contracts from a clean checkout.

Issue 27 added one more, because the cross-role journeys need a running target rather than a build:

```bash
TRACKING_MAP_STYLE_URL=http://127.0.0.1:9099/style.json docker compose up --build --wait
npm --prefix web run e2e
docker compose down
```

The Playwright journeys live in `web/e2e/` inside the existing frontend project rather than in a third build, and `npm run check` type-checks them. What each of them is evidence for, and what none of them claims, is [`docs/testing.md`](../../testing.md).

Issue 28 added two shell checks in `scripts/`, because the claims they back are about the repository and the deployment rather than about the code:

```bash
scripts/scan-repository.sh                        # credentials, tokens, addresses, coordinates
scripts/check-deployment.sh <base-url>            # headers, refusals and cookies of a running target
```

Both are in CI: the scan as its own job over an unshallowed clone, and the deployment check inside the packaging job against the image it has just started. Neither needs a credential, which is what lets the second one be pointed at a public deployment by anybody.

## Explicitly absent from Core

- Redis, Kafka, PostGIS, WebFlux, WebSocket, microservices, Kubernetes, CQRS and event sourcing.
- JPA/Hibernate and a generic repository layer.
- External ETA/geocoding calls, Service Zones and route planning.
- Matching Round timers, invitations, interests, decline/cooldown and overrides.
- Tracking Link Rotation, Revocation, Reissue and a link-administration history UI.
- Durable Courier coordinate history, background-location claims and three-role realtime fan-out.

Those omissions are preserved as [Future Work Issues 13–19](../map.md#future-work), not silently forgotten work.
