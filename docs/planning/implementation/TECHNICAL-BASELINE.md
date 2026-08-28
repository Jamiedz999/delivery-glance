# Technical baseline

The one place that says what the stack is, how the repository is laid out, and where the module seams
are. [ADR 10](../../adr/10-choose-core-technical-architecture.md) says why.

## The stack

| Concern | Choice |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Maven Wrapper, Spring MVC, Spring Security, Bean Validation, `JdbcClient` |
| Database | PostgreSQL 18, HikariCP, Flyway only. No JPA/Hibernate, no runtime schema generation |
| Sessions | Opaque same-origin Spring Security sessions, Spring Session JDBC; secure cookies and CSRF stay on |
| Frontend | Node 24, React 19, strict TypeScript, Vite, React Router, TanStack Query |
| Live updates | HTTP commands and queries plus same-origin `EventSource`. `SseEmitter` sends refresh hints, never state |
| Map | Bundled `maplibre-gl`. The tile URL is a deployment input and carries no Delivery or link token |
| Testing | JUnit, AssertJ, Testcontainers, Vitest, Testing Library, Playwright, axe-core |
| Packaging | Docker Compose for local PostgreSQL; one multi-stage image holding the app and the compiled assets |
| Operations | Actuator health, request correlation, redacted logs. No monitoring platform required |

Versions are pinned in the lock and build files. A patch bump goes in its own dependency PR with the
full suite; it does not reopen the architecture decision.

## Repository shape

```text
delivery-glance/
├── .github/workflows/ci.yml
├── server/          Maven Wrapper, pom.xml, src/main + src/test, db/migration
├── web/             package.json, lockfile, tsconfig, vite.config.ts, src/, e2e/
├── lambda/          proof-processor, notification-sender
├── infra/           deployment definitions
├── scripts/         scan-repository.sh, check-deployment.sh
├── compose.yaml     plus compose.proof.yaml and compose.notify.yaml
└── Dockerfile
```

- Development uses Vite's `/api` proxy to Boot so it looks same-origin.
- Production builds the React assets into the Boot jar and serves everything from one origin.
- `scripts/` holds checks with no build to belong to, because what they assert is not about the code:
  `scan-repository.sh` is about this repository's contents and history, `check-deployment.sh` is about
  a running deployment neither Maven nor npm knows the address of. Plain shell, no dependencies, so
  anybody can read one before pointing it at their own host.
- No root script that only composes the commands below. That would be a second build system saying
  the same thing twice.

## Modules and seams

The backend is one executable made of business modules, not `controller/service/repository` layers
spanning the whole application.

| Module | Owns | Interface exposed to peers |
|---|---|---|
| `system` | build and runtime proof | read-only system status |
| `identityaccess` | Staff Accounts, roles, session policy | current actor and authorization checks |
| `delivery` | Delivery data, lifecycle, Status Changes | guarded commands and role-specific queries |
| `courier` | Courier identity, name, On Duty | availability commands, coordinate-free facts |
| `location` | sharing intent and the one latest position | report/stop commands, age-filtered reads |
| `dispatch` | availability, nearest-three shortlist, atomic assignment | recommend and assign |
| `trackinglink` | link derivation, verifier, Copy, expiry, revocation, sessions | Dispatcher link commands, Viewer authorization |
| `recipientview` | the privacy-reduced Recipient projection | one authorized snapshot query |
| `eta` | ETA Windows and the travel-time port | window reads and recalculation triggers |
| `notification` | the outbox, relay and opt-in subscriptions | one write on a Status Change |
| `proof` | proof references and upload authorization | capture and reference reads |
| `demo` | putting the demo back to its start | one Dispatcher-only reset, only when the demo switch is on |

Implementation classes, SQL repositories and DTOs stay package-private where Java allows. Controllers
call module interfaces; they never reach into another module's repository. Test through the interface.

A `shared` package may hold `Clock`, identifiers, error primitives and credential primitives —
issuing a random secret, digesting it, comparing in constant time — but never business services or
speculative abstractions. The test for a credential primitive is that it knows nothing about what the
secret means: `Secrets` arrived only when a second module needed the same three operations, and the
policy about what each secret authorizes stays in the module that owns it.

**Create a seam only when it hides real complexity or has a real second side.**

- `LatestLocationStore` was expected to need an interface with a production and a fake-clock
  implementation. It does not — a test moves the injected `Clock` by hand — so it is one class with
  no interface in front of it.
- Map rendering is behind one React component, because tests use a local no-network substitute and
  production uses MapLibre.
- Redis, Kafka, PostGIS, WebFlux, microservices and a generic event bus are not seams here. Their
  triggers are in [ADR 10](../../adr/10-choose-core-technical-architecture.md).

`demo` is the one module allowed to write another module's tables, and the exception is written here
rather than argued inside the class that takes it. `DemoResetRepository` names and empties the tables
owned by `delivery`, `dispatch`, `trackinglink`, `courier` and `location`. The alternative was a
`deleteEverything()` on each of those five — destructive methods living permanently in production
code, reachable by any caller, to serve one fixture. One class that exists only when the demo switch
is on, and that names every table it empties, is the smaller hole. It is bounded three ways: it may
only delete; it must not touch `internal_account`, Spring Session or Flyway's tables; and it creates
its fictional Deliveries through `delivery`'s ordinary use case, so demo data cannot take a shape the
product would refuse to make.

`location.SharedPositionReset` is the seam that exception still needs. Coordinates are never in the
database, so emptying `courier_location_sharing` would end every session and leave the positions
those sessions produced — which is why forgetting them is a method on the module that owns them.

## Runtime rules

- PostgreSQL is authoritative for accounts, Deliveries, Status Changes, Assignments and Tracking Link
  metadata.
- Process memory owns only the newest usable Courier position. Restarting makes location Unavailable
  until a fresh report arrives. That is intended.
- Raw coordinates never enter PostgreSQL, a queue, logs, metrics, traces or any audit table.
- HTTP changes durable facts. SSE carries only version hints; every reconnect re-reads an authorized
  snapshot.
- Unsafe authenticated requests use CSRF protection. Internal routes require a role. Recipient routes
  require only the Tracking Session and never inherit internal authority.
- The frontend receives role-specific DTOs. It never receives a broad domain object and hides fields
  with CSS.

## Build contracts

These must stay green:

```bash
(cd server && ./mvnw verify)
npm --prefix web ci
npm --prefix web run check
docker compose up --build --wait
curl --fail --silent http://localhost:8080/actuator/health
curl --fail --silent http://localhost:8080/api/system
docker compose down
```

`npm run check` runs TypeScript checking, unit tests once, and a production build. CI runs all of it
from a clean checkout.

The cross-role journeys need a running target rather than a build:

```bash
TRACKING_MAP_STYLE_URL=http://127.0.0.1:9099/style.json docker compose up --build --wait
npm --prefix web run e2e
docker compose down
```

They live in `web/e2e/` inside the frontend project rather than in a third build, and `npm run check`
type-checks them. What each is evidence for, and what none of them claims, is
[`docs/testing.md`](../../testing.md).

Two shell checks back claims about the repository and the deployment rather than about the code:

```bash
scripts/scan-repository.sh                        # credentials, tokens, addresses, coordinates
scripts/check-deployment.sh <base-url>            # headers, refusals and cookies of a running target
```

Both are in CI: the scan as its own job over an unshallowed clone, the deployment check inside the
packaging job against the image it just started. Neither needs a credential, which is what lets the
second one be pointed at a public deployment by anybody.
