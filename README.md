# Delivery Glance

A recipient-first delivery tracking product. The current Sprint 2 increment lets a Dispatcher
create a Delivery, see the nearest three Eligible Couriers and make one atomic Direct Assignment.
The assigned Courier explicitly confirms pickup and handoff while foreground Location Sharing keeps
only the newest usable position. Tracking Links are the next increment — see
`docs/planning/issues/` for the implementation queue.

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

Each account only reaches its own workspace: the Dispatcher API refuses a Courier session with
`403`, and the Courier API refuses a Dispatcher session the same way, even when the route is
requested directly.

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

## Cumulative Sprint 2 demo path

This walkthrough carries one fictional Delivery through the whole internal flow:

1. `docker compose up --build --wait`
2. Sign in as the Courier, press **Go on duty**, then **Start sharing** and allow browser location.
   Leave this foreground page open so the latest position stays usable.
3. In another browser profile, sign in as the Dispatcher and create Delivery `DG-1001`. Use a
   pickup coordinate close to the Courier's current test location and any fictional handoff address.
4. Open the Delivery. The fresh recommendation shows the Courier's derived distance but never their
   coordinates. Press **Direct assign**. The Delivery moves from Awaiting Courier to Assigned once.
5. Return to the Courier workspace. The current Delivery shows both readable addresses. Press
   **Confirm pickup** to move it to In Transit, then **Confirm handoff** to move it to Delivered.
6. Reopen the Dispatcher detail. Its history contains all four states and actors, and there is no
   active Assignment left for either the Delivery or Courier.

Before pickup, the Dispatcher may instead cancel an Awaiting or Assigned Delivery with a reason;
that ends any active Assignment atomically. Cancellation is refused after pickup.

`docker compose down -v` also removes the database volume, which resets the demo.

## Courier demo path

On Duty is durable; a shared position is not. That difference is the point of this part of the
product, and a restart is the quickest way to see it:

1. Sign in as the Courier at `http://localhost:8080` and press **Go on duty**. Nothing has asked
   for your location yet, and nothing will until you ask it to.
2. Press **Start sharing**. Only now does the browser ask for permission. The page reports the
   newest position it has about every ten seconds, and only while it is in front of you — switch to
   another tab and reporting pauses honestly rather than pretending to continue.
3. Watch the position age: `Live` for thirty seconds, then `Delayed`, and at two minutes the server
   deletes the coordinates and shows `Unavailable`. The countdown is that deletion.
4. Restart only the application: `docker compose restart app`. You are still On Duty, but the
   position is `Unavailable` — coordinates live in memory alone, so there is nothing to restore.
5. Press **Stop sharing**, or simply sign out. Either removes the coordinates immediately rather
   than letting them age out.

Reloading the page always returns to Sharing off: the one-time reporting secret exists only in the
page that started the session, so a new page load cannot resume it.

## Verification commands

These are the checks CI runs on every push; run them locally before opening a PR:

```bash
(cd server && ./mvnw verify)
npm --prefix web ci
npm --prefix web run lint
npm --prefix web run check
docker compose up --build --wait
curl --fail --silent http://localhost:8080/actuator/health
curl --fail --silent http://localhost:8080/api/system
docker compose down
```
