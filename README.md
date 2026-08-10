# Delivery Glance

A recipient-first delivery tracking product. This repository currently contains the Core
walking skeleton plus the first vertical slice: a pre-provisioned Dispatcher signs in, creates a
Delivery, lists Deliveries, reopens a persisted detail and cancels it while it is still awaiting a
Courier. Courier availability, assignment, location sharing and Tracking Links are not built yet —
see `.scratch/recipient-delivery-tracking/issues/` for the implementation queue.

## Prerequisites

- Java 25
- Node 24
- Docker (with Compose)

## Demo accounts

Two fictional Internal Accounts are seeded by the first Flyway migration. There is no
registration, invitation or password reset; these are the only two accounts.

| Role | Email | Password |
|---|---|---|
| Dispatcher | `dispatcher@delivery-glance.example` | `Dispatcher-Demo-2026!` |
| Courier | `courier@delivery-glance.example` | `Courier-Demo-2026!` |

They are configurable through the `DEMO_*` variables in `.env.example` (emails, display names and
bcrypt password hashes). Because Flyway seeds them, changed values only apply to a database that
has not run the migration yet — remove the `postgres-data` volume to reseed.

The Courier account exists so the role boundary can be demonstrated: signing in as the Courier
shows a placeholder workspace, and the Dispatcher API refuses that session with `403` even if the
route is requested directly.

## Local development

Start PostgreSQL only:

```bash
docker compose up postgres --wait
```

Run the backend (reads `server/src/main/resources/application.yml`, connects to the Postgres
instance above using the same defaults as `.env.example`):

```bash
cd server
./mvnw spring-boot:run
```

Run the frontend with hot reload; Vite proxies `/api` and `/actuator` to the backend on
`http://localhost:8080`:

```bash
cd web
npm install
npm run dev
```

Open `http://localhost:5173`.

## Production-like run

Build and run the single application image plus PostgreSQL:

```bash
docker compose up --build --wait
```

Open `http://localhost:8080`, or check it directly:

```bash
curl --fail --silent http://localhost:8080/actuator/health
curl --fail --silent http://localhost:8080/api/system
```

Stop and remove the containers:

```bash
docker compose down
```

## Demo path

Deliveries live in PostgreSQL, and sessions are stored there too, so both survive an application
restart:

1. `docker compose up --build --wait`
2. Open `http://localhost:8080` and sign in as the Dispatcher.
3. Create a delivery — a reference such as `DG-1001`, plus a readable address and a WGS84
   coordinate for pickup and handoff.
4. Open the delivery from the list and note its URL.
5. Restart only the application: `docker compose restart app`.
6. Reload the same URL. The delivery, its history and your session are still there.
7. Cancel it with a reason while it is still awaiting a courier; the history records who cancelled
   it and why, and the delivery can no longer be changed.

`docker compose down -v` also removes the database volume, which resets the demo.

## Verification commands

These are the checks CI runs on every push; run them locally before opening a PR:

```bash
./server/mvnw verify
npm --prefix web ci
npm --prefix web run check
```
