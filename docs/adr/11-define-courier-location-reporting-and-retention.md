# Define Courier location reporting and retention

Type: grilling
Status: resolved
Blocked by: 07

## Question

When does a Courier explicitly start and stop Location Sharing; how do On Duty, an Active Delivery, browser permission, foreground interruption, report cadence, accuracy, and out-of-order readings affect collection; and what minimum Current/Last Known Location data or raw reports may be retained or deleted without creating a Route History product?

## Answer

> **Portfolio Core scope update:** [Ticket 12](12-rescope-to-resume-ready-core.md) keeps explicit foreground sharing, newest-only memory, freshness and deletion. The complete interruption vocabulary and thirty-day sharing audit remain preserved here as Later Backlog detail.

### Product boundary

Core uses an explicit, foreground-only Location Sharing Session. On Duty, an Active Delivery, and Location Sharing remain independent facts:

- On Duty means willing to receive Match Invitations; it neither grants browser permission nor starts Location Sharing.
- An Active Delivery means the Courier is responsible for an Assigned or In Transit Delivery; it does not make background tracking technically possible or remove the Courier's right to stop sharing.
- Location Sharing means the Courier has deliberately started a session in the currently open Courier workspace and the product may request and report positions while a valid purpose exists.

Collection is permitted only while the Courier is On Duty or has an Active Delivery. There is no pre-duty collection, passive collection after both purposes end, native background promise, or hidden route recording.

### Sharing states and controls

The Courier workspace exposes four honest states:

| State | Meaning |
|---|---|
| `OFF` | No sharing session exists and no position is collected. |
| `STARTING` | The Courier pressed `Start sharing`; permission or the first usable reading is pending. |
| `REPORTING` | The foreground page can obtain and submit readings. A usable Current Location still depends on age and accuracy. |
| `INTERRUPTED` | Sharing intent remains, but foreground execution, connectivity, or the position source cannot currently produce reports. |

The transition from `OFF` to `STARTING` always requires the Courier to press `Start sharing`. The action is available when the Courier is On Duty or has an Active Delivery, explains foreground-only behaviour before prompting, and cannot claim success until browser permission is granted. Going On Duty, accepting Match Interest, winning a Matching Round, or confirming pickup never starts it implicitly.

The Courier may press `Stop sharing` at any time. During an Active Delivery the product warns that Recipient tracking and ETA will become unavailable, but it cannot force collection; stopping does not withdraw, reassign, cancel, or complete the Delivery. The Dispatcher sees the interruption and its time so that it can be handled operationally.

The following also end the local sharing session:

- browser permission is denied or revoked;
- the Courier signs out;
- the page is closed or reloaded; and
- the Courier goes Off Duty while having no Active Delivery.

A newly loaded or newly signed-in page always starts in `OFF` and requires another explicit Start action. A close signal is not assumed to reach the server reliably; server-visible freshness still ages from the last accepted report.

The current state uses a small structured reason vocabulary so every role receives consistent wording. End reasons are `COURIER_STOPPED`, `PERMISSION_DENIED`, `PERMISSION_REVOKED`, `SIGNED_OUT`, and `NO_COLLECTION_PURPOSE`. Interruption reasons are `PAGE_HIDDEN`, `NETWORK_UNAVAILABLE`, `POSITION_UNAVAILABLE`, `POSITION_TIMEOUT`, `LOW_ACCURACY`, and `REPORTS_MISSING`; an unknown browser failure uses `OTHER`. Page close and device suspension may be observed only as missing reports rather than as a reliable end event.

Going Off Duty during an Active Delivery prevents new matching but does not end the Assignment. An already-running sharing session may continue for that Delivery until the Courier stops it or the Delivery ends. When a Delivery becomes terminal, sharing may continue only if the Courier is still On Duty; otherwise it stops and the position is cleared. Reassignment applies the same rule: an On Duty Courier may continue sharing for future matching, while an Off Duty Courier has no remaining collection purpose.

### Foreground interruption and recovery

Core collects only while the Courier workspace is visible in the foreground. If the page is hidden, the device suspends it, connectivity is lost, or the location provider fails, the UI moves to `INTERRUPTED` and states the known reason. It does not pretend to be reporting or change On Duty or the Delivery lifecycle.

Returning to the same still-open page may resume attempts under the existing sharing intent; a new page load requires explicit Start. The client does not build an offline queue of coordinates. After reconnection it may submit only the newest available measurement, so recovery cannot upload a burst that recreates a route.

A passive interruption does not rewrite the last accepted report. Its age determines the already-agreed behaviour: Live through thirty seconds, Delayed after thirty seconds through two minutes, then Unavailable. The Courier becomes ineligible when that report is more than two minutes old. In contrast, an explicit Stop or permission withdrawal invalidates the usable position immediately rather than leaving the Courier matchable for two more minutes.

### Reporting and acceptance contract

While `REPORTING`, the foreground page submits the first reading promptly and then targets one newest reading approximately every ten seconds. This is a target rather than an exact timer or a promise that the browser will supply a new fix. The product never fabricates intermediate positions or sends stored catch-up points.

Each attempted report carries the Courier and sharing-session identity, coordinates, accuracy radius, device-recorded measurement time (`recordedAt`), and an idempotency value. The server adds `receivedAt`. A report can replace Current Location only when all of the following hold:

- its coordinates and timestamps are structurally valid and not implausibly future-dated;
- accuracy is one hundred metres or better;
- `recordedAt` is newer than the stored usable reading; and
- the sharing session and collection purpose are still valid when the report is accepted.

Ordering uses `recordedAt`, never network arrival order. An older delayed report and an idempotent retry are harmless no-ops. For equal measurement times, the smaller accuracy radius may replace the larger one; otherwise the existing snapshot remains. `receivedAt` supports diagnostics but cannot make an old measurement fresh.

A `recordedAt` more than thirty seconds after `receivedAt` is implausibly future-dated and rejected. A report already more than two minutes old on receipt is stale and its coordinates are discarded. These bounds prevent a bad device clock or delayed upload from extending eligibility or public freshness.

A reading poorer than one hundred metres does not move Current Location, refresh Location Freshness, affect ranking, or feed ETA. The product records only the non-coordinate fact that a low-accuracy attempt occurred, its time, and its accuracy radius so the Courier, Dispatcher, and Recipient can see honest degradation. Invalid and stale attempts likewise do not become location history.

### Effect on matching, ETA, and Recipient tracking

- A Courier can be Eligible only with On Duty, Service Zone coverage, no Active Delivery, and a usable report no more than two minutes old. Stopping sharing or losing permission clears that usable report immediately; a passive interruption lets it age normally.
- Assigned ETA may use the same current usable position internally, but the Recipient still sees no Courier marker before pickup.
- While In Transit, the Recipient sees only the accepted Current/Last Known Location under the established Live, Delayed, and Unavailable thresholds.
- A poor reading leaves the prior marker at its prior timestamp and shows low accuracy; it never makes the old marker look newer.
- An explicit Stop or permission withdrawal, once detected, immediately removes the Courier marker from connected views and makes location-derived ETA unavailable. A disconnected Recipient page already shows `Reconnecting` and removes the marker no later than its local two-minute freshness limit; the view may disclose the last-success time without retaining or exposing its coordinates.
- Tracking Connection remains the Recipient page's connection to automatic updates, not evidence that Courier reporting is healthy.
- A terminal transition removes all Courier location and ETA from the Tracking Link immediately, regardless of whether the Courier continues sharing for future matching.

### Data minimisation and retention

Core does not persist raw position reports as an append-only table, event stream, log, trace, analytics record, or Delivery audit. It maintains at most one usable coordinate snapshot per Courier, containing only coordinates, accuracy, `recordedAt`, `receivedAt`, and the identifiers needed to validate its sharing session. Accepting a newer usable report atomically replaces the old snapshot; it never appends another point.

The usable coordinate is removed from every serving store and cache at the earliest of:

- explicit Stop, browser-permission withdrawal, or sign-out;
- the moment there is neither On Duty nor an Active Delivery purpose;
- two minutes after its `recordedAt` when no replacement arrives; or
- invalidation of the sharing session that produced it.

After coordinate removal, the product may retain only the last-success time, last attempt outcome, accuracy radius, and interruption reason needed to explain an ongoing Active Delivery. That coordinate-free status is deleted when the Delivery becomes terminal or ceases to be Active. If the Courier remains On Duty, a new accepted reading creates a new current snapshot for matching.

Raw request data exists only long enough to validate and atomically update that snapshot, then is discarded. Poor, invalid, duplicate, out-of-order, and more-than-two-minute-old report coordinates are never durably stored. Application logs, error reports, metrics labels, traces, Recommendation Decisions, and Delivery Transitions must not contain coordinates. Recommendation Decisions may retain the already-agreed derived distance and ranking rationale, not the source position.

Current Location is ephemeral operational state and is not required to survive a service restart or enter a database backup. After restart it is Unavailable until a fresh report arrives; authoritative Delivery and Assignment state remains durable. One non-coordinate audit record per sharing session may retain who started it, its start and end times, and its structured end reason for thirty days. The current interruption reason may be overwritten for UI and diagnostics, but there is no per-report, per-interruption, or recovery history.

Expansion inherits the same no-history rule. It may partition, cache, or replicate latest-value state for measured scale, but every serving component must converge on at most one snapshot per Courier and apply the same deletion triggers. A component that requires append-only raw coordinates, replayable position events, or a durable coordinate queue is outside the six-week scope; it cannot be introduced as an invisible implementation detail.

### Required acceptance scenarios

The prototype and architecture must demonstrate at least these cases:

1. On Duty without Start remains ineligible and collects nothing; Start plus a usable fix makes the Courier eligible.
2. Permission denial, mid-session revocation, and explicit Stop leave the Delivery unchanged, make location unavailable honestly, and remove stored coordinates.
3. A hidden page and network loss stop reports; the last point moves through Live, Delayed, and Unavailable without extrapolation, then a foreground recovery accepts only the newest fix.
4. Poor, duplicate, future-dated, and out-of-order readings never move or refresh Current Location.
5. Going Off Duty with no Active Delivery stops collection, while going Off Duty during an Active Delivery keeps the Assignment and lets the Courier choose whether the existing sharing session continues.
6. Assignment, pickup, Reassignment, Delivered, Cancelled, and Undeliverable apply the purpose and cleanup rules without exposing location outside In Transit.
7. Storage, logs, traces, backups, and APIs contain no queryable raw Route History, and cleanup removes an expired or deliberately withdrawn coordinate on schedule.
