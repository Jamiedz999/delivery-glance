# Delivery Glance

A recipient-first delivery tracking product. The current Sprint 3 increment lets a Dispatcher
create a Delivery, see the nearest three Eligible Couriers and make one atomic Direct Assignment.
The assigned Courier explicitly confirms pickup and handoff while foreground Location Sharing keeps
only the newest usable position. Every Delivery also carries a Tracking Link, which gives whoever
holds it a privacy-reduced view of that one Delivery — and nothing else — until it expires. See the
[implementation queue](https://github.com/Jamiedz999/delivery-glance/issues) on GitHub.

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

## Recipient demo path

A Tracking Link is a capability, not an account: anyone holding it sees one Delivery, and holding
it grants nothing anywhere else in the application. This walkthrough is the Recipient's half of the
Sprint 3 gate, and it is best followed on a phone on the same network.

1. Run the Sprint 2 demo path above as far as step 4, so one Delivery is Assigned.
2. Ask the API for the link. There is no button for this yet — the endpoint is the whole of what
   Core has, and the missing control is recorded in
   `docs/planning/implementation/INCIDENTAL-FINDINGS.md`:

   ```bash
   curl -s -c jar http://localhost:8080/api/system > /dev/null
   csrf() { awk '/XSRF-TOKEN/ {print $7}' jar; }
   curl -s -b jar -c jar -H "X-XSRF-TOKEN: $(csrf)" \
     -d email=dispatcher@delivery-glance.example -d 'password=Dispatcher-Demo-2026!' \
     http://localhost:8080/api/session/login
   curl -s -b jar -c jar -H "X-XSRF-TOKEN: $(csrf)" -X POST \
     "http://localhost:8080/api/deliveries/<delivery-id>/tracking-link/copy"
   ```

   The URL it returns carries its capability in the fragment — the part after `#` — which RFC 3986
   keeps out of every HTTP request.
3. Open it in a private window, or send it to a phone. The page exchanges the fragment for a
   short-lived cookie and removes it from the address bar and from history before showing anything.
   Pressing Back does not walk into a URL that still carries the token.
4. While the Delivery is Assigned you see its Reference, the Handoff Address and a limited Courier
   Display Name. There is no map: a Courier heading to a pickup is not information about this
   Delivery's journey.
5. Have the Courier confirm pickup. Reload the Recipient page. It now shows the map, the Courier's
   last reported position with its accuracy, and `Live location`. Leave the page alone and watch it
   age by itself — `Delayed` after thirty seconds, and at two minutes the marker disappears and the
   page says `Location unavailable`. Nothing arrived from the server to cause that; the page will not
   claim to know where somebody is on the strength of a two-minute-old reading.
6. Press **Stop sharing** in the Courier workspace and reload the Recipient page: the marker is gone
   immediately, and the handoff marker remains.
7. Have the Courier confirm handoff. The Recipient page keeps the Reference, the Handoff Address and
   the actual handoff time, and loses the Courier's name and every trace of location.
8. Cancel a different Delivery before pickup and open its link. It shows only that it was cancelled,
   when, and who to contact — no Reference, no address and no internal reason.

Tampering with the fragment, or opening a link more than seven days old, produces one identical
response in every case, which is what stops the page becoming a way to ask whether a Delivery exists.

### The map is optional

`TRACKING_MAP_STYLE_URL` is a release input and is unset by default, so the demo above runs without
a tile provider: step 5's status, freshness and accuracy text is all present, and the map itself is
replaced by an honest unavailable state. Set it to any absolute MapLibre style URL to see the map —
the page adds that URL's origin to its own Content-Security-Policy, which is why it must be absolute.
`DELIVERY_TEAM_CONTACT` is the single contact step 8 offers, and is likewise empty by default.

## Verification commands

These are the checks CI runs on every push; run them locally before opening a PR:

```bash
(cd server && ./mvnw verify)
npm --prefix web ci
npm --prefix web run format:check
npm --prefix web run lint
npm --prefix web run check
docker compose up --build --wait
curl --fail --silent http://localhost:8080/actuator/health
curl --fail --silent http://localhost:8080/api/system
docker compose down
```
