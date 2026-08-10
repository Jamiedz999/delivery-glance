# DG-022 · Add Courier duty and latest-only Location Sharing

Type: implementation
Sprint: 2
Area: full-stack
Blocked by: DG-021
Estimate: 5–6 focused hours

## Outcome

A signed-in Courier independently goes On Duty, explicitly starts foreground Location Sharing, reports only the newest usable browser position, sees freshness, and can stop sharing so the server immediately forgets the coordinates.

## Read first

- [Core technical baseline](../implementation/TECHNICAL-BASELINE.md)
- [Ticket 11: location reporting and retention](../../adr/11-define-courier-location-reporting-and-retention.md)
- [Ticket 12: simplified Current Location](../12-rescope-to-resume-ready-core.md#current-location)
- [Core flow logic prototype](../prototypes/delivery-glance-core-flow-logic-prototype.html)

## In scope

Introduce `courier` and `location` with a narrow interface between them.

- On Duty is durable, Courier-controlled and independent of Location Sharing.
- `POST /api/couriers/me/location-sharing` explicitly starts a generation and returns a one-time high-entropy reporting secret under `Cache-Control: no-store`; PostgreSQL stores only its verifier and coordinate-free current generation metadata.
- `POST /api/couriers/me/location-reports` accepts generation, reporting secret, longitude, latitude, accuracy and device-recorded time.
- `DELETE /api/couriers/me/location-sharing` immediately invalidates the generation and removes its in-memory snapshot. Sign-out performs the same removal.
- `PUT /api/couriers/me/duty` changes only On Duty.
- `GET /api/couriers/me` returns the Courier's duty/sharing/freshness presentation without returning a reusable reporting secret.

`LatestLocationStore` has one production in-memory implementation and one deterministic fake-clock test implementation. A value is an immutable complete snapshot. Reject invalid coordinates, accuracy worse than 100 metres, readings older than two minutes on receipt, readings over thirty seconds in the future, wrong generations/secrets, duplicates and out-of-order readings. Equal measurement time replaces only when accuracy improves.

The Courier page calls `watchPosition()` only after Start, while visible, sends at roughly ten-second cadence, keeps only the newest reading and sends no recovery backlog. Reload has no reporting secret and returns the UI to Sharing Off. Stop or sign-out removes coordinates immediately. A cleanup task is helpful but every read must independently enforce the two-minute boundary.

Show `Live` through 30 seconds, `Delayed` through two minutes and `Unavailable` after that. Use a visible countdown based on measurement time.

## Acceptance criteria

- The cumulative canonical checks stay green.
- Fake-clock tests cover valid replacement, out-of-order/duplicate rejection, accuracy and timestamp bounds, 30-second/two-minute boundaries, generation change and immediate Stop cleanup.
- An integration test proves no coordinate column/table exists and no raw coordinate appears in captured application logs for report success or rejection.
- Frontend tests prove browser permission is requested only after Start, no report is sent while hidden, only the newest pending fix is sent after recovery, and reload without the in-memory secret shows Sharing Off.
- Manual browser simulation can toggle On Duty separately, Start, move from Live to Delayed/Unavailable under a test clock, and Stop with immediate removal.
- Application restart makes location Unavailable and does not reconstruct coordinates from PostgreSQL.

## Non-goals

- Assignment, recommendation, Recipient location, background tracking or native mobile behaviour.
- Route History, durable raw coordinates, Kafka, Redis or multi-instance sharing.
- Full interruption reason/audit vocabulary, operating-system notification or exact timer guarantees.

## PR evidence

Include the fake-clock test names, schema search showing no coordinate storage, and a short Courier-page demo. After merge, promote only DG-023.
