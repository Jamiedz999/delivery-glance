# Deploying Delivery Glance

Status: current

Delivery Glance is one container plus one PostgreSQL database. That is the whole topology, and it is
deliberate — see [`architecture.md`](architecture.md#why-redis-kafka-postgis-webflux-and-full-matching-are-not-here).
Anywhere that can run an OCI image with a few environment variables and terminate TLS in front of it
will do, so this runbook says what the application requires rather than which provider to buy.

Pick your target, then read the requirements below as a checklist. What you actually type depends on
the provider; what has to be true does not.

## What you are deploying

```bash
docker build -t delivery-glance:<revision> .
```

One multi-stage image. It compiles the React assets, builds the Boot jar with those assets inside it,
and serves everything — the Dispatcher and Courier application, the Recipient tracking page and the
API — from a single origin on port `8080`.

`compose.yaml` is the local development stack and is **not** the deployment. It publishes PostgreSQL
on `5432` and defaults every secret to a development value.

## Requirements

### 1 · A PostgreSQL 18 database, and nothing else stateful

Point the application at it and Flyway does the rest on first start: it creates every table,
including Spring Session's, and seeds the two fictional Staff Accounts. There is no runtime DDL
and no manual migration step.

```
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<database>
SPRING_DATASOURCE_USERNAME=<user>
SPRING_DATASOURCE_PASSWORD=<password>
```

The user needs `CREATE` on its schema, because Flyway owns it.

**Run exactly one instance.** Current Location and the Recipient SSE subscribers live in
process memory, so a second instance would hold its own copy of both and the two would disagree.
This is a stated limit of Core rather than an oversight; sharing them is
#32.

### 2 · TLS in front of it, and the cookies told about it

A Tracking Link is a public bearer capability. Serving it over plain HTTP would put that capability
in clear on the wire, so **HTTPS is not optional** for anything anybody outside your machine will
open.

Terminate TLS at the platform's router, a reverse proxy, or whatever your target provides, and
forward to the container's `8080`. Then tell the application:

```
SESSION_COOKIE_SECURE=true
```

The Tracking Session cookie already defaults to `Secure` in `application.yml`; it is the plain-HTTP
local demo that opts out, which is the right way round for a bearer capability. So there is nothing
to set for it, and `TRACKING_COOKIE_SECURE` should simply be left alone in a deployment.

Redirect plain HTTP to HTTPS at the same layer. The application does not do it, because a container
behind a proxy cannot reliably tell what scheme the browser used.

### 3 · Real Tracking Link key material

Every Tracking Link is an HMAC derived from this key. The default is a development string and is
committed on purpose, so it is worthless — a link derived from it is forgeable by anyone who has read
this repository.

```bash
# 256 bits, base64. Generate it where you will store it, not in a shell you keep.
openssl rand -base64 32
```

```
TRACKING_KEY_V1=<that value>
TRACKING_CURRENT_KEY_VERSION=1
```

Changing this value invalidates every link already issued under version 1. To roll a key without
breaking live links, add a version 2 key in `application.yml` and move
`TRACKING_CURRENT_KEY_VERSION` to 2; the key version is recorded per link, so old links keep
verifying under the old key.

### 4 · Secrets supplied outside Git

Every value in section 3 and the database password go in the platform's secret store — environment
variables set on the service, a secret manager, or CI secrets. None of them belong in a file in this
repository.

What is committed is `.env.example`, which contains only development defaults and says so. `.env`
itself is gitignored. Confirm before your first deploy:

```bash
git check-ignore -v .env                            # must print a .gitignore rule
git log --all --diff-filter=A --name-only -- .env   # must print nothing
```

The full scan, including history, is in [the README](../README.md#privacy-and-what-is-never-stored).

### 5 · The health check

```
GET /actuator/health   →   200 {"status":"UP"}
```

That is the only Actuator endpoint exposed; everything else under `/actuator/**` is denied. Point the
platform's health probe at it. It covers the datasource, so an instance that cannot reach PostgreSQL
reports itself unhealthy rather than serving errors.

Give the first start a generous grace period — Flyway runs the whole migration set against an empty
database before the port opens.

### 6 · The map is a release input

```
TRACKING_MAP_STYLE_URL=
```

Unset is a **supported deployment**, and the one this project's public demo runs. The Recipient view
keeps every word of its status, next step and freshness content and shows an honest map-unavailable
state in place of the map. Nothing is faked and no test is skipped.

To enable the map, set it to an absolute MapLibre style URL. Two requirements come with that:

- **Absolute**, because the page adds that URL's origin to its own Content-Security-Policy so tiles
  can be fetched. A relative URL cannot name an origin.
- **Restricted at the provider.** Whatever key the URL carries is public — it is in a page anybody
  holding a Tracking Link can read. Restrict it at the provider to your deployment's hostname (an
  HTTP referrer restriction on most tile providers) and to the tile endpoints only, and give it the
  smallest quota that serves the demo. Treat it as a rate-limited public identifier, not a secret.

The style URL must carry no Delivery identifier and no Tracking token. `scripts/check-deployment.sh`
checks that.

### 7 · The Delivery Team contact

```
DELIVERY_TEAM_CONTACT=
```

The one phone number or email address a Recipient is given when a Delivery was cancelled. One value
for the whole team, never anything derived from the Delivery. Blank is supported — the page then
points them back at whoever shared the link.

### 8 · The demo reset, if this is the public demo

```
DEMO_RESET_ENABLED=true
```

Adds `POST /api/demo/reset`, which **deletes every Delivery, Assignment, Tracking Link and Courier
fact** and recreates the two fictional Deliveries. It is Dispatcher-only and CSRF-protected, but the
demo credentials are published in the README, so treat it as reachable by anyone who reads that far.

That is fine for a demo whose every row is fictional and whose whole purpose is to be driven, and it
is the reason the switch exists: a hosted database usually cannot be dropped and recreated the way
`docker compose down -v` drops a local volume.

**Leave it off anywhere else.** Off is the default in the application, in `compose.yaml` and in
`.env.example`, and with it off the route is refused by the security policy rather than merely
unmapped.

It does **not** touch the two Staff Accounts, and that is a deliberate narrowing of what a reset
might be expected to do. They are seeded by `V1__internal_account.sql` and are already fictional;
recreating them would mean the application carrying bcrypt hashes at runtime and becoming a second
source of truth for credentials that Flyway already owns. So a reset restores the demo's *data* and
leaves the way in alone — which is also what lets the Dispatcher who pressed it stay signed in to see
the result.

### 9 · Nothing else

There is no Redis to provision, no message broker, no object store, no separate frontend host and no
CDN to configure. If a deployment guide for this application is longer than this page, something has
been added that Core does not use.

The one deliberate exception is **proof of delivery** ([Issue 50](https://github.com/Jamiedz999/delivery-glance/issues/50)),
a portfolio-expansion epic that consciously extends this section: it is the first feature to store
binary artifacts, and it introduces a private S3 bucket and one processing Lambda. It is **off unless
a bucket is configured** — the settings under `delivery-glance.proof` default to blank, `/api/system`
then reports `proofCaptureEnabled: false`, and the application behaves exactly as this page otherwise
describes. Turn it on only where you have provisioned the bucket, the Lambda and the IAM that lets the
application presign and the Lambda read, write and call back (see
[`lambda/proof-processor/README.md`](../lambda/proof-processor/README.md)). To demonstrate the whole
loop locally without an AWS account, run the LocalStack overlay:

```bash
docker compose -f compose.yaml -f compose.proof.yaml up --build
```

Nothing above depends on any of this: the default `docker compose up`, the deployment check and the
journeys all run with proof disabled, exactly as they did before the epic.

The second deliberate exception is **delivery notification** ([Issue 51](https://github.com/Jamiedz999/delivery-glance/issues/51)),
the portfolio-expansion epic that reverses Core's no-Recipient-contact stance under one narrow term:
the Recipient volunteers an email or phone from the tracking page (see
[ADR 13](adr/13-notify-recipient-off-band.md)). It introduces a transactional outbox, an SQS queue
with a dead-letter queue, and one consumer Lambda that sends through SES/SNS
([`lambda/notification-sender/README.md`](../lambda/notification-sender/README.md)). It is **off unless
a queue is configured** — the settings under `delivery-glance.notification` default to blank, the
tracking page's opt-in then reports itself unavailable, and no contact is ever captured or sent. A
deployment that leaves it off holds no Recipient contact at all, exactly as Core always has. Turn it on
only where you have provisioned the queue, its DLQ, the Lambda, a verified SES sender and the IAM that
lets the application send to the queue and the Lambda call back. To demonstrate the whole loop locally
without an AWS account, run the LocalStack overlay:

```bash
docker compose -f compose.yaml -f compose.notify.yaml up --build
```

As with proof, nothing above depends on it: the default compose, the deployment check and the journeys
all run with notification disabled. **No Courier coordinate ever enters the queue** — the message body
is a transition id and nothing more.

The third deliberate exception is **external travel-time ETA** ([Issue 27](https://github.com/Jamiedz999/delivery-glance/issues/27)),
the portfolio-expansion epic that restores the arrival window ADR 05 designed and Core deferred: while a
Courier is on the way, the Recipient page shows a rounded provisional or current window, ages it to an
honest "temporarily unavailable" past five minutes, and keeps a missed window visible as "running later
than expected". It calls a contract-approved travel-time provider behind a project-owned port — the
reference provider is **Mapbox Directions** ([ADR 10](adr/10-choose-core-technical-architecture.md)) —
and stores only the two rounded window endpoints and a calculation time. **No route geometry, polyline,
waypoint or Courier coordinate is ever stored or shown**; the provider is asked for a duration with
`overview=false`, and the provider's response types never leave the adapter. It is **off unless a
provider base URL is configured** — the settings under `delivery-glance.eta` default to blank,
`/api/system` then reports `etaEnabled: false`, the tracking page shows no arrival section, and the
application computes no windows and makes no external call, exactly as Core ran before this feature.

Turn it on only once the operational gate is met, because this is the one feature whose external
dependency carries licensing and money:

- **A contract-approved provider account.** Record the provider's **terms of service**, its **DPA and
  privacy disclosure** (the application sends two coordinates per active Delivery to the provider and
  receives a duration — no Recipient identity), and confirm the plan permits this use.
- **A billing cap.** Set `ETA_DAILY_REQUEST_CAP` to a ceiling the account can afford; the adapter stops
  calling the provider once a day's requests reach it and leaves windows to age out rather than run up a
  bill. Zero — the default — is uncapped and is only for tests and the local demo.
- **The provider inputs**: `ETA_PROVIDER_BASE_URL` (`https://api.mapbox.com` for the reference
  provider), `ETA_ACCESS_TOKEN`, and optionally `ETA_REQUEST_TIMEOUT` and `ETA_REFRESH_INTERVAL`.

A provider fault is never allowed to matter to a Delivery: the estimate is computed after a transition
commits, holding no lock, and any failure — a timeout, a rate limit, an outage — degrades the window
rather than rolling anything back. Nothing above depends on ETA being on: the default compose, the
deployment check and the journeys all run with it disabled.

## Deploy

1. Build the image from a **clean checkout** at the revision you intend to run.
2. Create the database and set every variable above on the service.
3. Start one instance and wait for `/actuator/health`.
4. Run the check below.
5. Walk [the demo script](demo-script.md) against the public URL.
6. Only then tag the revision.

## Verify

```bash
scripts/check-deployment.sh https://your-host.example
```

It needs no credentials, so it can be run against any deployment, by anyone, at any time. It asserts:

| Group | What it checks |
|---|---|
| Transport | the base URL is HTTPS |
| Answering | `/actuator/health` is UP; `/api/system` answers; `/api/deliveries` refuses an anonymous caller with `401` |
| Cookies | the CSRF cookie is `Secure` and `SameSite=Strict` |
| Tracking route | `/track` is served with `no-store`, `no-referrer`, `nosniff`, `noindex` and a CSP that forbids framing |
| Tracking refusal | an invalid token gets the generic `404`, with the same headers and no Delivery field in the body |
| Inputs | reports whether a map style is configured, and that the reset is not reachable unauthenticated |

Exit status is 0 only if every required check passed. The two `note` lines are deployment decisions
and never fail the run.

Then confirm the two properties that need a restart to see:

```bash
# Delivery truth is durable; a shared position is not.
# Before: a Courier sharing, a Recipient page showing Live location.
# Restart the instance, then reload nothing.
```

The Delivery, its state and its history all survive. The Courier is still On Duty. Their location is
`Unavailable` until a fresh report arrives, and the Recipient's page reconnects and says so. That is
the design working, not a fault — [`architecture.md`](architecture.md#current-location-in-process-memory)
explains why.

## Rolling back

The image is stateless. Deploy the previous revision and it comes back as it was, because everything
durable is in PostgreSQL and every migration so far has been additive. What does not come back is any
Courier’s Current Location, for the same reason a restart loses it.

Flyway has no `clean` and no `undo` here. A migration that has to be reversed needs a new forward
migration.
