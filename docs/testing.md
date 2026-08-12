# What Delivery Glance's tests actually prove

Status: current

This file exists so that every claim the project makes about itself can be traced to a command
anybody can run, and so that the claims it does **not** make are written down beside them. A
portfolio project's tests are read as evidence, and evidence that nobody can reproduce — or that
proves something narrower than the sentence it is attached to — is worse than none.

Two rules hold everywhere below:

- **No number appears here that a committed command did not produce.** There is no coverage
  percentage, no throughput figure and no latency figure, because nothing in this repository
  measures one.
- **Each row names the risk, not the feature.** "Assignment works" is not a risk. "Two Dispatchers
  assign the same Courier at the same moment and both succeed" is.

## Running everything

```bash
# Backend: unit tests, module tests and real-PostgreSQL integration tests (Testcontainers)
(cd server && ./mvnw verify)

# Frontend: type checking, component and unit tests, production build
npm --prefix web ci
npm --prefix web run format:check
npm --prefix web run lint
npm --prefix web run check

# The production-like target the journeys run against, configured with the map style they serve
TRACKING_MAP_STYLE_URL=http://127.0.0.1:9099/style.json docker compose up --build --wait
curl --fail --silent http://localhost:8080/actuator/health
curl --fail --silent http://localhost:8080/api/system

# The two cross-role journeys and the accessibility checks
npm --prefix web run e2e
docker compose down
```

`npx playwright install --with-deps chromium` is needed once before the first `e2e` run. CI runs all
of the above from a clean checkout on every push and pull request.

## The risk matrix

### Assignment concurrency

| Risk | Proved by | What it actually asserts |
|---|---|---|
| Two Dispatchers assign the same Courier at the same instant and the Courier ends up carrying two Deliveries | `dispatch/AssignmentConcurrencyTest.simultaneousAssignmentsGiveOneCourierOnlyOneActiveDelivery` | Two real HTTP assignments released from a latch against real PostgreSQL. Exactly one answers `204` and the other `409`; the database holds exactly one active `assignment` row for that Courier, and the losing Delivery is left coherent. Repeated three times per run, each repeat building its own Couriers and Deliveries. |
| Two Dispatchers assign different Couriers to one Delivery and it ends up with two | `dispatch/AssignmentConcurrencyTest.simultaneousAssignmentsGiveOneDeliveryOnlyOneActiveCourier` | The same race the other way round, plus the Delivery finishing at `ASSIGNED:1` with exactly two transitions — so the loser left no history behind. |
| The application's own eligibility check is trusted instead of the database | `SchemaOwnershipTest.databaseArbitratesOneActiveAssignmentPerCourierAndDelivery` | The two partial unique indexes exist and are what refuses the second writer. |
| A retried command assigns twice | `dispatch/DispatchApiTest.directlyAssignsOnceAndTreatsARetryAsTheSameCommand` | The same `commandId` twice produces one Assignment. |

**Not proved:** any throughput or latency figure. This is a correctness race with two contenders, not
a load test, and no benchmark exists in this repository. Ticket 12 keeps a measured scale experiment
in Future Work 18 for exactly this reason.

### Delivery lifecycle

| Risk | Proved by | What it actually asserts |
|---|---|---|
| A Delivery moves through a transition Core does not allow | `delivery/DeliveryStateTest.definesEveryAllowedAndRejectedCoreTransition` and `delivery/DeliveryApiTest.deliveryModuleRefusesAssignmentFromAnyStateExceptAwaitingCourier` | The whole transition table, allowed and rejected, in one place. |
| A Delivery is cancelled after pickup | `dispatch/DispatchApiTest.dispatcherCanCancelAnAssignedDeliveryBeforePickupAndEndItsAssignment` | Cancel ends the Assignment atomically before pickup, and is refused afterwards. |
| A stale page overwrites a newer state | `delivery/DeliveryApiTest.rejectsACancelThatExpectedAnOlderVersion`, `treatsARetriedCancelCommandAsTheSameCommand` | Version conflicts are refused; retries of one command are not second commands. |
| A Courier progresses somebody else's Delivery | `dispatch/DispatchApiTest.anotherCourierCannotProgressTheAssignedDelivery`, `tellsAnotherCourierNothingAboutTheDeliveryWhenTheVersionIsAlsoWrong` | Refused, and refused without disclosing the Delivery's real version. |

### Location privacy and freshness

| Risk | Proved by | What it actually asserts |
|---|---|---|
| A raw Courier coordinate reaches durable storage | `location/LocationPrivacyTest.hasNoColumnAnywhereThatCouldHoldACourierPosition`, `storesNeitherTheCoordinatesNorTheReportingSecretOfAnAcceptedReport` | Every column in the schema is inspected, and an accepted report leaves nothing behind in any of them. |
| A raw coordinate reaches the log | `location/LocationPrivacyTest.keepsRawCoordinatesOutOfTheApplicationLog` | The captured application log after a real report contains neither value. |
| A route history accumulates | `location/LatestLocationStoreTest` (14 assertions, including `ordersByMeasurementTimeEvenAcrossLocationSharingSessions`, `forgetsACourierImmediatelyOnStop`, `startsEmptySoARestartLeavesNoCoordinateBehind`) | At most one snapshot per Courier, replaced rather than appended, gone on Stop, and gone on restart. |
| A stale or implausible reading is treated as a position | `location/LatestLocationStoreTest.rejectsAReadingLessAccurateThanOneHundredMetres`, `rejectsAReadingMeasuredMoreThanThirtySecondsInTheFuture`, `rejectsAReadingAlreadyOlderThanTwoMinutesOnReceipt`, `rejectsAMeasurementOlderThanTheStoredOne` | The whole acceptance contract, each boundary named. |
| The two-minute deletion depends on a sweeper actually running | `location/LatestLocationStoreTest.forgetsAnExpiredReadingWithoutWaitingForTheSweep` | The read itself deletes an expired snapshot. |
| Live/Delayed/Unavailable drift apart between roles | `location/LocationFreshnessTest` and `web/src/freshness.ts` used by both the Courier page and the Recipient page | One set of boundaries on the server, one in the browser, and the browser's is a single module both roles import. |
| Someone else's page reports a position for a Courier | `courier/CourierApiTest.refusesAReportFromAnEarlierSession`, `refusesAReportThatCannotProveTheSessionSecret`, `refusesAReportForAnUnknownGeneration` | Generation and secret are both required, and a wrong one of either is answered identically. |
| A reporting secret can be read back | `courier/CourierApiTest.issuesAReportingSecretOnceAndNeverReturnsItAgain` | Issued once; only a verifier is stored. |

### Tracking Link

| Risk | Proved by | What it actually asserts |
|---|---|---|
| A raw token or a shareable URL is persisted | `trackinglink/TrackingLinkPrivacyTest.hasNoColumnAnywhereThatCouldHoldARawTokenOrATrackingUrl`, `storesOnlyDigestsThatCannotBeTurnedBackIntoACapability` | Schema-wide, and the stored verifier is one-way. |
| A raw token reaches the log | `trackinglink/TrackingLinkPrivacyTest.keepsRawTokensOutOfTheApplicationLog` | The captured log after a real exchange contains no token. |
| The token reaches the server in a request-target | `trackinglink/TrackingLinkApiTest.buildsTheLinkAsAFragmentSoTheTokenNeverReachesTheServerInARequest` | The capability is in the fragment; RFC 3986 keeps it out of every request. |
| A tampered, unknown or expired link tells the holder which it was | `trackinglink/TrackingLinkApiTest.answersUnknownMalformedAndExpiredTokensWithOneIndistinguishableResponse` plus the browser half in `web/e2e/degradation.spec.ts` | One identical response for every failure, and — in a real browser — no Reference, no address, no Courier name, and the Recipient application never even downloaded. |
| A link outlives what it is for | `trackinglink/TrackingLinkExpiryTest` (5 boundary cases) and `trackinglink/TrackingLinkApiTest.stopsAcceptingALinkSevenDaysAfterItWasIssued`, `stopsAcceptingALinkTwentyFourHoursAfterTheDeliveryIsCancelled`, `refusesTheExchangeOnTheExpiryInstantItselfRatherThanFailingOnIt` | Seven-day cap, terminal grace period, and the expiry instant itself, against a controlled server clock. |
| Copy issues a new capability each time | `trackinglink/TrackingCapabilitiesTest` (12 derivation cases) and `TrackingLinkApiTest.returnsTheSameLinkEveryTimeTheDispatcherCopiesIt` | The HMAC is deterministic per link generation and key version, and changes when any of those change. |
| A Tracking grant confers internal authority, or a session confers Recipient access | `trackinglink/TrackingLinkApiTest.keepsInternalSessionsAndTrackingGrantsFromStandingInForEachOther` | Neither substitutes for the other. |
| Guessing a token is cheap | `trackinglink/TrackingAttemptsTest` (6 cases, including `neverExpiresOrDisablesTheLinkItProtects`) | Failures throttle the source that is guessing and nobody else, and never damage the link. |

### Role isolation and transport

| Risk | Proved by |
|---|---|
| A Courier reaches Dispatcher routes, or a Dispatcher reaches Courier routes | `delivery/DeliveryRouteAuthorizationTest`, `courier/CourierRouteAuthorizationTest` (10 cases across both) |
| An unauthenticated request reaches anything it should not | `system/SecurityConfigTest.deniesUnmappedApiPaths`, `deniesUnmappedActuatorPaths`, `deniesNonGetRequestsToSystemEndpoint` |
| An unsafe request succeeds without CSRF | `system/SecurityConfigTest.deniesUnsafeRequestsWithoutACsrfToken` plus per-route cases in both authorization tests |
| Sign-in tells an attacker which half was wrong | `identityaccess/SessionApiTest.answersAWrongPasswordAndAnUnknownEmailIdentically`, `refusesADisabledInternalAccountWithTheSameFailure` |
| The tracking responses are cached, indexed or referrer-leaking | `trackinglink/TrackingLinkApiTest.sendsTheAgreedCacheReferrerIndexingAndContentHeadersOnEveryTrackingResponse`, `sendsTheHeadersEvenOnResponsesTheSecurityChainWritesWithoutReachingAHandler` |
| The bootstrap page's CSP drifts from the script it serves | `trackinglink/TrackingBootstrapPageTest.pinsTheScriptAndStyleItActuallyServesRatherThanAHashWrittenDownBesideThem` |

### Recipient projection and stream

| Risk | Proved by | What it actually asserts |
|---|---|---|
| The Recipient is sent a field their state does not allow | `recipientview/RecipientSnapshotsTest.carriesExactlyTheFieldsItsStateAllowsAndNoOthers` and `recipientview/RecipientViewApiTest.neverShipsAnIdentifierAPickupAddressOrAnInternalDetailInAnyState` | Field by field, state by state, over the real API. |
| A Courier appears on the map before pickup | `recipientview/RecipientViewApiTest.namesTheCourierOnceAssignedButStillPutsNothingOnAMap` | Assigned names the Courier and draws nothing. |
| A handed-over Delivery keeps a trace of the Courier | `recipientview/RecipientViewApiTest.removesTheCourierAndEveryTraceOfLocationTheMomentTheDeliveryIsHandedOver` | Delivered keeps the Reference, the address and the time, and nothing else. |
| A cancelled Delivery leaks its internal reason or its address | `recipientview/RecipientViewApiTest.showsACancelledDeliveryItsReferenceOutcomeTimeAndWhoToAskAndNoAddress` | Generic outcome, one contact, no address, no reason. |
| One Recipient's page hears about another's Delivery | `recipientview/RecipientStreamApiTest.neverTellsOnePageAboutAnotherDeliverysChange`, `tellsTheChangedDeliverysPageToRefetchAndTellsItNothingElse` | The hint carries a version and nothing else, and reaches only the page it is about. |
| A refused or rolled-back command still notifies | `recipientview/RecipientStreamApiTest.saysNothingWhenACommandWasRefused`, `saysNothingWhenTheTransactionThatReportedAChangeRolledBack`, `saysNothingWhenAReportedLocationWasNotAccepted` | Only a change that actually happened produces a hint. |
| A page that missed changes stays wrong | `recipientview/RecipientStreamApiTest.givesAPageThatMissedAChangeTheCurrentTruthWhenItComesBack` and the browser half in `web/e2e/degradation.spec.ts` | Reconnect rereads one authorized snapshot; nothing is replayed because nothing needs to be. |
| A grant that stopped authorising keeps its stream | `recipientview/RecipientStreamApiTest.refusesAStreamToAGrantThatHasExpired`, `endsAStreamWhoseGrantStoppedAuthorisingIt`, `leavesNothingBehindWhenAPageGoesAway` | Rechecked on a heartbeat, and cleaned up. |

### The two cross-role journeys

Both run in Chromium against the built Compose image, at the viewports the roles actually use — a
desktop Dispatcher and two phones — with `workers: 1` and **no retries**, because a suite that
passes on the second attempt is not evidence.

| Journey | File | What it walks through |
|---|---|---|
| Happy path | `web/e2e/happy-path.spec.ts` | Dispatcher signs in, creates and directly assigns; Courier goes on duty, shares, picks up, moves and hands over; the Recipient follows all of it through a Tracking Link on a phone that is opened once and never reloaded. Asserts the fragment is spent and removed from history, that Assigned shows a name and no map, that In Transit draws both markers with a live freshness sentence, and that Delivered removes the Courier and every trace of location. |
| Degradation | `web/e2e/degradation.spec.ts` | Stop sharing removes the marker at once over a live stream; sharing resumes; the phone goes offline and the application restarts under it, so the stream is genuinely severed; the reading then ages Live → Delayed → Unavailable **on the phone's own clock**, with the marker leaving the map while the page still says `Reconnecting for updates…`; the network returns and one snapshot catches the page up; and a tampered link reveals nothing. |

The Recipient's clock is moved with Playwright's browser clock. Nothing in the application knows
about it: there is no test profile, no override header and no injected time source, so what the
journey exercises is the shipped build. The Courier's positions are emulated Geolocation at
fictional coordinates, and the Delivery References are generated per run because a Reference is
unique for the life of the database.

**How the journeys reset:** through the API, as a signed-in Courier. `web/e2e/support/journey.ts`
finishes whatever Delivery the Courier is still holding and ends duty and sharing. There is no reset
endpoint and no test-only route.

### Accessibility

`web/e2e/accessibility.spec.ts`, against the same running image.

| Risk | Proved by |
|---|---|
| A surface has machine-detectable accessibility violations | axe-core over the Dispatcher list, one Dispatcher Delivery with its recommendation panel, the Courier workspace holding a Delivery and sharing, and the Recipient page In Transit. The suite fails on any **serious or critical** violation, and at the time of writing every surface reports none at any impact level. The rules axe applies to these pages include `color-contrast`, `button-name`, `link-name`, `heading-order`, `region`, `landmark-one-main` and `page-has-heading-one`. |
| A keyboard user cannot complete the Dispatcher's core task | A walkthrough that signs in, reaches the form from the list and fills every field in tab order, typing only — asserting at each stop that the focused element is still drawn as focused. If reading order and tab order ever disagree, it puts an address into a latitude field and fails. |
| Motion is forced on a reader who asked for less | A context with `reducedMotion: 'reduce'` opens the Recipient page, and every element in the rendered document is checked for a non-zero animation or transition. Nothing animates today — the map is framed on its markers at load and deliberately never eased afterwards — so this asserts a property rather than a media query. |

## What is not tested, and is not claimed

These are limits, not oversights. Each one is here because somebody reading the repository could
otherwise assume the opposite.

- **No performance, latency, throughput or scale figure exists.** Nothing measures one. The
  concurrency test is a two-contender correctness race.
- **No coverage percentage is published or enforced.** The matrix above is the coverage claim: risks
  named, each with a command. A global percentage would say less and drift more.
- **One browser engine.** The journeys run in Chromium at a desktop and a phone viewport. WebKit,
  Firefox and real devices are untested, and Ticket 12 rules an exhaustive matrix out of Core.
- **The map's tiles are never fetched.** The journeys serve MapLibre an empty style with a background
  layer, so what is proved is that the map mounts inside the page's Content-Security-Policy and that
  markers are added and removed. No tile provider is exercised, and none is committed to.
- **The no-map deployment is covered by component tests, not by a journey.** `TRACKING_MAP_STYLE_URL`
  unset is a supported deployment and the Recipient page then says the map is unavailable;
  `web/src/track/TrackingPage.test.tsx` covers it. The E2E target is deliberately run *with* a style
  so that marker removal can be observed at all.
- **Link expiry is proved on the server, not in a browser.** A seven-day link cannot be aged from a
  page, and adding a way to do it would be the production backdoor this work rules out.
  `TrackingLinkExpiryTest` and `TrackingLinkApiTest` prove it against a controlled clock; the journey
  proves only that a refused link is indistinguishable and reveals nothing.
- **Accessibility is checked, not certified.** Automated rules find machine-detectable failures.
  There has been no screen-reader session, no assistive-technology matrix and no audit against WCAG
  as a whole.
- **Nothing here tests a deployed environment.** Every command runs against a local Compose image.
  Hosting, TLS, the public hostname and a production tile provider are release inputs.
- **Known gaps found while writing these tests are recorded, not silently fixed**, in
  [`planning/implementation/INCIDENTAL-FINDINGS.md`](planning/implementation/INCIDENTAL-FINDINGS.md).
  The Courier workspace not learning about a new Assignment while it stays visible is the one most
  likely to surprise somebody following the demo.
