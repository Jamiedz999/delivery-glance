# ADR 10 — One application, one database, and location only in memory

## The question

What shape should this be built in, given three roles, a public bearer link, live location, and one
person building it?

## What we decided

A single Spring Boot application serving the compiled React assets from the same origin, with one
PostgreSQL database. No microservices, no WebSocket, no Redis, no Kafka, no PostGIS, no event
sourcing.

The rule everything follows:

> **PostgreSQL decides what happened. Process memory holds only where a Courier is now. HTTP changes
> facts. SSE only tells a browser to read the facts again.**

Nothing in memory, in a browser cache or in a provider response can become a second source of truth
about a Delivery.

### The stack

| Concern | Choice |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Spring MVC, Spring Security, `JdbcClient`, Bean Validation |
| Database | PostgreSQL 18, HikariCP, Flyway as the only way the schema changes |
| Sessions | Opaque server-side sessions via Spring Session JDBC; secure cookies and CSRF stay on |
| Frontend | React 19, strict TypeScript, Vite, React Router, TanStack Query |
| Live updates | Same-origin `EventSource`; Spring MVC `SseEmitter` on a bounded executor |
| Map | `maplibre-gl` bundled in; the tile URL is a deployment input |
| Testing | JUnit, AssertJ, Testcontainers, Vitest, Testing Library, Playwright, axe-core |
| Packaging | One multi-stage image holding the app and the compiled assets |

`JdbcClient` and hand-written SQL rather than JPA. The persistence work that matters here is guarded
state changes, `FOR UPDATE`, partial unique indexes and conditional inserts — keeping that SQL visible
is smaller than adding an ORM and then bypassing it on every important path.

### The Tracking Link

The link is **derived, never stored**. Each generation has a random internal id, a generation number
and a key version; the token is `HMAC-SHA-256` over those with a deployment key that lives outside
the database, the image and the repository:

```text
rawToken = HMAC-SHA-256(keyRing[keyVersion], (linkId, generation, keyVersion))
urlToken = base64url(rawToken)      // 43 characters, carried in the URL fragment
verifier = SHA-256(rawToken)        // the only thing stored
```

Copy Link re-derives the same token and checks it against the verifier. A database copy cannot be
turned back into a working link.

`GET /track` returns generic HTML with no Delivery data and never looks a link up. The fragment never
reaches the server — the bootstrap script reads it, POSTs it once, and calls `history.replaceState`
to remove it whether that succeeds or fails. What comes back is a `Secure`, `HttpOnly`, `SameSite=Lax`
cookie holding no link data.

Unknown, malformed, expired and revoked tokens all take the same failure path and produce the same
data-free page. Redemption attempts are rate-limited per address; a valid link is never revoked by
somebody else's guesses, because that would be a denial-of-service dressed as a defence.

Tracking responses send `no-store`, `no-referrer`, `noindex`, `frame-ancestors 'none'` and a
route-specific CSP. No analytics, session replay or third-party script runs on that page.

### Live updates

SSE carries a **refresh hint, not the truth**: a version number saying "read again". Every fact on
the Recipient's screen still arrives through the same authorised snapshot read the page does on load.

So a reconnect needs no replay, and a bug in the stream cannot leak anything, because the stream
carries nothing to leak. Every reconnect repeats authorisation. A restart produces a new process
epoch, which forces a refresh and clears any old location before the new snapshot is applied.

## Why

Every piece of infrastructure not in that table was left out for the same reason: it would make the
application harder to run and no better at this size, while adding a place for a coordinate to be
written down.

Each one has a written trigger instead:

| Not here | What would have to be true first |
|---|---|
| Redis | More than one instance is needed, or the in-memory location store is measurably the bottleneck |
| PostGIS | Profiling shows distance and zone work dominates, or polygons get complicated |
| WebFlux | After tuning the MVC executor, blocking response writes are still the specific failure |
| Kafka | An independent consumer needs durable replay of non-coordinate events — see [ADR 13](13-notify-recipient-off-band.md) |
| Self-hosted routing | The provider quota or a data-residency requirement forces it |

These are not a bundle. Each needs its own failed baseline and its own before-and-after evidence, and
each would get its own ADR recording the measured bottleneck and the rollback path.

## What is built

All of it. Modules are top-level packages with package-private internals and a small interface:
`identityaccess`, `courier`, `delivery`, `dispatch`, `location`, `trackinglink`, `recipientview`,
`eta`, `notification`, `proof`.

The `location` module is the one that carries the privacy promise: the Dispatcher sees a distance
derived server-side, and the coordinate never leaves that module.

**No performance or scale figure exists for this application, anywhere.** Nothing in the repository
measures one, so the triggers above are conditions to test for, not thresholds that have been
checked.
