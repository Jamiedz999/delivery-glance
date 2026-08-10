# DG-020 · Scaffold the full-stack walking skeleton

Type: implementation
Sprint: 1
Area: foundation, full-stack
Blocked by: none
Estimate: 4–5 focused hours

## Outcome

From a clean checkout, a contributor can run one React application, one Spring Boot application and PostgreSQL; the browser proves the frontend can read a same-origin backend endpoint, and CI proves the same builds and tests without any Delivery business logic.

This is the first implementation Issue. It establishes a trustworthy repository boundary for every later Agent and is intentionally smaller than the Sprint 1 product slice.

## Read first

- [Core technical baseline](../implementation/TECHNICAL-BASELINE.md)
- [Implementation Issue workflow](../implementation/ISSUE-WORKFLOW.md)
- [Ticket 12: resume-ready Core](../12-rescope-to-resume-ready-core.md)
- [Lean roadmap prototype](../prototypes/delivery-glance-lean-roadmap-prototype.html)

## Repository contract

Create the exact top-level shape defined by the technical baseline: `server/`, `web/`, `.github/workflows/ci.yml`, `compose.yaml`, `Dockerfile`, `.env.example`, `.gitignore` and `README.md`.

- `server/` uses the committed Maven Wrapper, Java 25 and Spring Boot 4.1 with MVC, Security, Validation, JdbcClient, PostgreSQL, Flyway, Actuator and test dependencies needed now.
- `web/` uses React, strict TypeScript and Vite with a committed npm lockfile. Add React Router and TanStack Query, but no component library, map library or state framework yet.
- PostgreSQL runs through Compose with a named application database and a health check. Secrets come from local environment/default development values documented in `.env.example`; no real secret is committed.
- The multi-stage Dockerfile builds `web/`, packages its assets inside the Boot jar and produces one runtime image.
- Vite proxies `/api` and `/actuator` to Boot in development. The production image serves both API and browser-history routes from one origin.

Do not pre-create empty business modules. The only initial Java module is `system`, because it owns a real vertical proof.

## Interface

Add one endpoint:

```http
GET /api/system
200 OK
Content-Type: application/json

{"application":"delivery-glance","status":"ok"}
```

`/api/system`, `/actuator/health` and the frontend shell are temporarily public. Every other request is denied by default. The React start page calls `/api/system` through TanStack Query and renders an accessible “Frontend connected to Delivery Glance API” success state plus visible loading and failure states.

Flyway must connect and validate successfully against PostgreSQL, but the first domain migration belongs to DG-021. Do not create a probe table just to make the migration folder non-empty.

## Ordered work

1. Scaffold and pin the backend and frontend build files plus wrappers/lockfile.
2. Implement the `system` endpoint, secure-by-default route rules and its backend test.
3. Implement the React connection page and its loading/success/error unit tests.
4. Add PostgreSQL Compose configuration and a Testcontainers integration test proving Boot, JdbcClient and Flyway start against the supported database.
5. Add the production asset packaging and one-image Docker path with health checks.
6. Add CI using the canonical backend/frontend checks, then document local development and production-like Compose commands in `README.md`.

## Acceptance criteria

- A clean checkout contains no generated build output or secrets.
- `./server/mvnw verify` passes and includes a PostgreSQL Testcontainers startup test.
- `npm --prefix web ci` and `npm --prefix web run check` pass; `check` runs TypeScript, unit tests once and the production build.
- `docker compose up --build --wait` starts healthy PostgreSQL and the single application image.
- `curl --fail --silent http://localhost:8080/actuator/health` reports `UP` without exposing unnecessary component detail.
- `curl --fail --silent http://localhost:8080/api/system` returns the contract above.
- Opening `http://localhost:8080/` renders the React success state; refreshing a client-side route still serves the app.
- CI runs the same backend and frontend verification from a clean checkout.
- `docker compose down` leaves the workspace clean; generated files remain ignored.

## Non-goals

- Internal Accounts, login, Delivery tables or any Delivery screen.
- JPA, Redis, Kafka, WebFlux, WebSocket, PostGIS, a map provider or deployment hosting.
- A design system, production navigation, broad exception framework or speculative module interfaces.
- GitHub repository creation, secrets, public hostname and cloud deployment; those are owner-controlled release inputs.

## PR evidence

Include the clean-checkout command results, a screenshot of the connected page and the final repository tree. The PR must not contain work from DG-021.
