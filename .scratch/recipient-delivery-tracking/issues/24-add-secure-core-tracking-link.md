# DG-024 · Add the secure Core Tracking Link

Type: implementation
Status: blocked
Labels: blocked, core, sprint-3, security, full-stack
Blocked by: DG-023
Estimate: 4–5 focused hours

## Outcome

Every newly created Delivery has one repeatable, expiring, no-account Tracking Link. A holder exchanges the URL fragment once for a narrowly scoped same-origin session without putting a raw token in PostgreSQL, logs or later requests.

## Read first

- [Core technical baseline](../implementation/TECHNICAL-BASELINE.md)
- [Ticket 12: simplified Tracking Link](12-rescope-to-resume-ready-core.md#tracking-link-and-recipient-view)
- [Tracking Link security research and its current-scope note](../research/tracking-link-security.md)
- [Full link decision, using its Core scope update](06-define-tracking-link-lifecycle.md)

## In scope

Introduce `trackinglink`.

- On Delivery creation, generate random internal link identity/generation metadata and deterministically derive a full 256-bit HMAC capability from a versioned deployment key. Persist only metadata and a unique SHA-256 verifier, never the raw token.
- Effective expiry is the earlier of seven days after creation or twenty-four hours after a terminal Delivery transition. Viewing and Copy never extend it.
- Dispatcher-only `POST /api/deliveries/{id}/tracking-link/copy` rederives and verifies the same current capability and returns `/track#t=<base64url-token>` under `Cache-Control: no-store`. Record only coordinate/token-free Copy actor/time evidence required by Core.
- `GET`/`HEAD /track` serve generic first-party bootstrap HTML and do not inspect, activate or consume a token.
- The bootstrap reads a strict fragment, posts it once to `POST /api/tracking-session`, immediately calls `history.replaceState` on success or failure, then loads the Recipient application only after success.
- A successful exchange creates a random `Secure`, `HttpOnly`, host-only, `SameSite=Lax` server-side grant scoped only to one link generation and bounded by its effective expiry. Development cookie security is an explicit local profile; production defaults secure.
- Unknown, malformed and expired tokens use one data-free Unavailable Link response. Use constant-time verifier comparison and bounded in-memory attempt limiting; never auto-expire a valid link because of failed guesses.
- Tracking bootstrap/API responses send `Cache-Control: no-store`, `Referrer-Policy: no-referrer`, `X-Robots-Tag`, `nosniff` and a route-specific CSP. No analytics, session replay, third-party script or service worker runs on `/track`.

Create the grant and authorization boundary now, but expose only a minimal authorised placeholder snapshot until DG-025.

## Acceptance criteria

- Deterministic tests with a fixed key prove repeated Copy returns the same token and changing link identity/generation/key version changes it.
- Database/log scans prove no raw token or complete Tracking URL is persisted or logged on creation, Copy, exchange, rejection or exception.
- Integration tests cover valid exchange, malformed/unknown/expired indistinguishability, Copy authorization, seven-day expiry, terminal-plus-24-hour expiry and grant expiry.
- Browser tests prove the token is present only in the initial fragment, is absent from the first HTTP request, disappears from address/history immediately, and is absent from all subsequent API requests.
- `GET` and `HEAD /track` cannot activate or consume the link, including repeated simulated preview requests.
- Internal Account sessions do not grant Recipient access, and a Tracking grant does not grant Dispatcher/Courier access.
- All tracking responses contain the agreed cache/referrer/indexing/content headers.

## Non-goals

- Rotation, Revocation, Reissue, link recovery UI, full link history, advanced guessing alerts or cross-instance rate limiting.
- PIN, Recipient account, email/SMS delivery, analytics or third-party code on the tracking route.
- Recipient map/content, ETA or SSE beyond the placeholder needed to prove authorization.

## PR evidence

Include security-test results and a browser trace showing the fragment disappears before protected content loads. After merge, promote only DG-025.
