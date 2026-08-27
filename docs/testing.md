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
# The repository itself: credentials and tokens across every blob on every ref, and addresses and
# coordinates in the working tree
scripts/scan-repository.sh

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

# The headers, refusals and cookies of whatever is running at that URL. The same command the
# deployment runbook points at a public host; over plain HTTP it says which checks it skipped.
scripts/check-deployment.sh http://localhost:8080

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
| A Courier is told nothing is being shared while their position is on a Recipient's map | `pages/CourierHomePage.test.tsx` — "reads the new session back before it collects anything" | Starting a session clears the server's position, so the workspace reads itself back; that read must complete **before** the first report, or it lands on top of it and replaces a live position with the emptiness it was sent to observe. Nothing corrects it afterwards, because the next report only happens if the device produces another reading. Fixed by this Issue; the test fails without the fix. |

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

### Proof of delivery

| Risk | Proved by | What it actually asserts |
|---|---|---|
| A raw image byte reaches PostgreSQL | `proof/ProofApiTest.mintsAPresignedUploadAndTheBrowserUploadsStraightToS3`, `handoffAttachesTheCapturedProofAsPendingReferencesInOneTransaction` | Against a real S3 (LocalStack), the browser PUTs straight to the bucket and the application only ever records object keys; `delivery_proof` holds references, a hash and times, never bytes. |
| The bucket serves anonymous reads | `proof/ProofApiTest.keepsTheBucketPrivateWithPublicAccessFullyBlocked` | The bucket has every public-access control on — the configuration that makes real S3 refuse an anonymous read. LocalStack does not emulate the anonymous-read authorization itself, so the reproducible cause is asserted rather than the S3-enforced effect; a read is only ever a per-object presigned GET. |
| A Courier captures proof for a Delivery they are not carrying, or forges a key onto a handoff | `proof/ProofApiTest.refusesAnUploadForADeliveryTheCourierIsNotCarrying`, `refusesAHandoffCarryingAKeyForAnotherDeliveryAndKeepsTheDeliveryInTransit` | The presign is refused for a Delivery the Courier does not hold, and a key for another Delivery rolls the handoff back rather than being recorded. |
| EXIF/GPS survives into stored proof | `lambda/proof-processor` — `test_scrub_strips_gps_and_every_other_exif_tag` | A GPS-carrying JPEG fixture comes out of the scrub with no GPS and no EXIF at all, and a bounded thumbnail is produced. |
| An invalid upload is served rather than quarantined | `proof/ProofApiTest.aRejectedCallbackLeavesTheDispatcherWithAStatusAndNoImageToLoad` | A rejected artifact carries a status and no URL to load; the Lambda moves the raw object to `quarantine/`. |
| The processing callback is not actually the Lambda | `proof/ProofApiTest.refusesAProcessingCallbackThatDoesNotCarryTheSharedToken` | No shared bearer token, no write: the callback is refused `401` before it can settle anything. |
| A Recipient is shown the image rather than only that proof exists | `recipientview/RecipientSnapshotsTest.tellsARecipientWhetherProofIsOnFileOnlyOnceDeliveredAndNeverTheImage` and its state matrix, plus `web/src/track/TrackingPage.test.tsx` | `proofOnFile` is a yes/no present only once Delivered — the privacy matrix fails if any other field appears — and the Recipient page renders only the reassurance line. |

### Role isolation and transport

| Risk | Proved by |
|---|---|
| A Courier reaches Dispatcher routes, or a Dispatcher reaches Courier routes | `delivery/DeliveryRouteAuthorizationTest`, `courier/CourierRouteAuthorizationTest` (10 cases across both) |
| An unauthenticated request reaches anything it should not | `system/SecurityConfigTest.deniesUnmappedApiPaths`, `deniesUnmappedActuatorPaths`, `deniesNonGetRequestsToSystemEndpoint` |
| An unsafe request succeeds without CSRF | `system/SecurityConfigTest.deniesUnsafeRequestsWithoutACsrfToken` plus per-route cases in both authorization tests |
| Sign-in tells an attacker which half was wrong | `identityaccess/SessionApiTest.answersAWrongPasswordAndAnUnknownEmailIdentically`, `refusesADisabledInternalAccountWithTheSameFailure` |
| The tracking responses are cached, indexed or referrer-leaking | `trackinglink/TrackingLinkApiTest.sendsTheAgreedCacheReferrerIndexingAndContentHeadersOnEveryTrackingResponse`, `sendsTheHeadersEvenOnResponsesTheSecurityChainWritesWithoutReachingAHandler` |
| The bootstrap page's CSP drifts from the script it serves | `trackinglink/TrackingBootstrapPageTest.pinsTheScriptAndStyleItActuallyServesRatherThanAHashWrittenDownBesideThem` |

### The demo reset

A route that deletes every Delivery, Assignment, Tracking Link and Courier fact is the most dangerous
thing in this application, and the risk it carries is not "does it work" but "can it exist somewhere
nobody asked for it".

| Risk | Proved by | What it actually asserts |
|---|---|---|
| A deployment that never asked for the demo has a data-wiping route | `demo/DemoResetDisabledTest.refusesTheResetForTheDispatcherWhoWouldOtherwiseBeAllowedIt`, `refusesTheResetForAnAnonymousCaller` | With the switch at its default, the exact caller who would otherwise be allowed it gets `403 access-denied`, and an anonymous one gets `401`. Refused by the security policy rather than merely unmapped — an unmapped `/api/**` path falls through to the frontend catch-all, which would answer a POST with the React shell. |
| The reset leaves data from the run before | `demo/DemoResetTest.replacesEveryDeliveryWithTheFictionalOnesAndSaysWhichItMade`, `endsTheCouriersDutyAndForgetsTheirSharedPosition`, `makesEveryTrackingLinkIssuedBeforeItUnusable` | A stray Delivery is `404` afterwards; the Courier is Off Duty with no sharing session; the in-memory position is `UNAVAILABLE` rather than surviving the database delete; and a link copied before the reset no longer exchanges. |
| The reset takes the accounts with it and locks the demo out | `demo/DemoResetTest.leavesTheTwoInternalAccountsAlone` | Both sign in again afterwards. |
| Demo data is written in a shape the product would never create | `demo/DemoResetTest.makesEachFictionalDeliveryTheSameWayTheDispatcherWouldHave` | Each fictional Delivery is `AWAITING_COURIER` at version 0 with exactly one transition attributed to a real actor, and has the Tracking Link that only the real creation path produces. Nothing is inserted straight into a later state. |
| A Courier can reset the demo mid-walkthrough | `demo/DemoResetTest.isRefusedForACourier`, `isRefusedWithoutTheCsrfHeaderEvenForTheDispatcher` | Dispatcher-only, and CSRF-protected like every other unsafe route. |

**Not proved:** that the endpoint is off in any particular deployment. That is a deployment input,
and `scripts/check-deployment.sh` reports only that it is unreachable without authentication — which
is all an outside caller can honestly establish.

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
passes on the second attempt is not evidence. `workers: 1` is not incidental: the Delivery Team is
two pre-provisioned accounts and its one Courier may hold one Active Delivery at a time, so parallel
journeys would race each other rather than the product.

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
finishes whatever Delivery the Courier is still holding and ends duty and sharing. It does not use
the demo reset and could not: that endpoint is off in the default deployment the journeys run
against, and a suite that needed it switched on would be evidence about a configuration nobody else
runs. There is no test-only route.

**`web/e2e/screenshots.spec.ts` is not part of this suite.** It drives the product the same way and
reuses the same fixtures, but it writes the pictures in `docs/screenshots/` rather than asserting
anything, so `playwright.config.ts` excludes it and `npm --prefix web run screenshots` is what runs
it. Its one assertion is a refusal: it stops if the database already holds `DEMO-1001`, so a
screenshot is never a picture of an earlier run's leftovers.

### States that must not claim success or leak

`web/e2e/honest-states.spec.ts`, plus the component tests named beside each row. These are the
states nobody demos, and each has the same two ways to be wrong: showing a finished-looking answer
before there is one, or refusing a question in words that answer it.

| Risk | Proved by | What it actually asserts |
|---|---|---|
| A Dispatcher who lost a Direct Assignment is shown the winner's result as if it were theirs | "the Dispatcher who loses a Direct Assignment is told, not congratulated", and `pages/DeliveryDetailPage.test.tsx` — "says so when another Dispatcher won the Delivery…" | Two Dispatcher windows on one Delivery; the loser is told the Delivery changed. The refusal used to be rendered inside the shortlist panel, which the refetch triggered by that same failure unmounted — so the page silently redrew as an assigned Delivery with the winner's Courier where a success would have put one. Fixed by this Issue. |
| A page still loading looks like an answer | "a page that is still loading does not look like an answer" | The Deliveries read is held open; the page shows its loading status and neither a table nor "No deliveries yet." |
| A refusal describes what it is refusing | "a Delivery that does not exist is refused without describing anything" | A well-formed identifier for nothing gets the generic sentence, no heading, and no echo of the identifier. |
| An empty workspace shows somebody else's work | "a Courier with nothing assigned is told so, and shown nobody else's Delivery" | A Delivery exists and belongs to no one; the Courier's page says nothing is assigned and does not name it. |
| A reconnecting page claims to be current, or throws away what it had | `web/e2e/degradation.spec.ts` | Covered in the journey above. |
| A refused Tracking Link says which kind of refusal it was | `web/e2e/degradation.spec.ts` and `trackinglink/TrackingLinkApiTest` | One sentence for every cause, announced as an alert by both the bootstrap and the application. |
| A deployment with no map style shows a blank box where a map was promised | `src/track/TrackingPage.test.tsx` — "says the map is unavailable rather than leaving a blank box when the style fails to load" and the empty-style case beside it | Component tests rather than a journey, because the E2E target is deliberately run *with* a style so marker removal can be observed at all. |

### Accessibility

`web/e2e/accessibility.spec.ts`, against the same running image.

| Risk | Proved by |
|---|---|
| A surface has machine-detectable accessibility violations | axe-core over the Dispatcher list, one Dispatcher Delivery with its recommendation panel, the Courier workspace holding a Delivery and sharing, and the Recipient page In Transit. The suite fails on any **serious or critical** violation, which is what "no violations" means here and is the whole of the claim. The rules axe applies to these pages include `color-contrast`, `button-name`, `link-name`, `heading-order`, `region`, `landmark-one-main` and `page-has-heading-one`. |
| A keyboard user cannot complete the Dispatcher's core task | A walkthrough that signs in, reaches the form from the list and fills every field in tab order, typing only — asserting at each stop that the focused element is still drawn as focused. If reading order and tab order ever disagree, it puts an address into a latitude field and fails. |
| Motion is forced on a reader who asked for less | The Recipient's phone in the scan above asks for `reducedMotion: 'reduce'`, and every element in the rendered document is checked for a non-zero animation or transition. It is asserted on the In Transit page specifically, because that is the only state in the product that draws a map. It caught one: MapLibre fades a marker in and out over two hundred milliseconds and its own reduced-motion rule covers only the user-location dot, so the Courier's marker faded away at the moment the page had decided it no longer knew where they were. `web/public/track-app.css` now turns that off under `prefers-reduced-motion`. |

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
