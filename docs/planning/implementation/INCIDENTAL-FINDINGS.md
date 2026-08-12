# Incidental findings

Status: current

Things noticed while implementing something else. They are real, they are not the Issue that
found them, and the alternative to writing them here is a PR body nobody rereads — which is exactly
how ADR 04's callout below went stale for a Sprint.

This file is **not** a queue. The [GitHub Issues](https://github.com/Jamiedz999/delivery-glance/issues) are the queue, it is fixed at nine Issues, and
nothing here becomes a GitHub Issue. An entry earns a plain PR when someone picks it up, the way
tooling work already does. Delete an entry when it is fixed; a list of things already done is worse
than no list.

Each entry says what is wrong, how it was found, and why it was not fixed on the spot.

## Open

### The Courier's freshness line says "1 seconds ago"

`web/src/pages/CourierHomePage.tsx:101` interpolates `{freshness.ageSeconds} seconds ago` with a
fixed plural, so the first second of every Location Sharing Session reads *Live — measured 1 seconds
ago*. It is the only place in the product that counts a unit in prose; the Recipient's page says
"updated just now" instead and never meets the problem.

Found while generating `docs/screenshots/courier-workspace.png` for DG-028 — the capture takes its
picture immediately after the first accepted report, which is exactly when the count is 1, so the
defect was going into the README. Not fixed there because DG-028 is packaging and a copy change in a
Courier page is a product change; the screenshot was retaken a second later instead, which hides it
rather than closing it.

Fix: either an `Intl.PluralRules`-aware helper beside `web/src/freshness.ts`, or the Recipient's
approach — "just now" under five seconds and a count after that. Whichever, `CourierHomePage.test.tsx`
should pin the one-second case, which it currently does not.

### A Courier watching their own workspace is never told they have been assigned

`web/src/pages/CourierHomePage.tsx` reads its current Delivery through `useCurrentCourierDelivery`,
and nothing refetches it. There is no polling, and React Query's focus refetch needs a
`visibilitychange` the browser only fires when the tab actually goes away — so a page that stays in
front of the Courier never asks again. That is the page the product asks them to keep in front of
them, because foreground Location Sharing stops the moment it is hidden.

The README's walkthrough works because its human switches browser profiles between roles, and a
reload works too — at the cost of ending sharing, which is the documented and correct consequence
of reloading. Neither is something a Courier watching for work would think to do.

Ticket 12 allows for it: "Dispatcher and Courier pages may refetch after their own commands and use
modest polling for changes." The polling was never implemented.

Found during DG-027, when the E2E journey's Courier stood waiting for an assignment that had already
happened; `web/e2e/support/workspace.ts` reloads and says why. Not fixed there because adding a poll
is new product behaviour, which `ISSUE-WORKFLOW.md` returns to refinement rather than letting a test
Issue introduce.

Fix: give `currentCourierDelivery` a modest `refetchInterval` while a Courier is On Duty, and
nothing while they are not — an off-duty Courier has no Delivery to hear about.

### The degradation journey can only run against the local Compose stack

`web/e2e/support/application.ts` shells out to `docker compose restart app`, from a path resolved
relative to the test file. It is there for a good reason — telling a browser it is offline gates new
connections but leaves an established stream open, so restarting is the only way to actually sever
one — but it means `E2E_BASE_URL` is a half-truth. Point the suite at a deployed environment and
seven of the eight tests do what they say while the degradation journey restarts whatever container
happens to be running on the machine holding the checkout.

Found while writing DG-027, once the offline route turned out not to work. Not fixed there because
the alternatives are all bigger than the Issue: a server-side "drop my stream" endpoint would be the
production backdoor DG-027 rules out, and a proxy the journeys could cut is a new moving part in the
harness.

Fix: either make the restart step skip itself with a stated reason when the target is not local, or
put the journeys behind a small proxy they control, so severing a connection is something the
harness can do wherever it points. Whichever way, `E2E_BASE_URL` should stop implying more than it
delivers.

### The production image build compiles the test harness

`web/tsconfig.json` references `tsconfig.e2e.json`, so `npm run build` — and therefore the
`web-build` stage of the `Dockerfile` — type-checks the Playwright journeys, and the image build now
fails if `@playwright/test` cannot be installed. That was a deliberate trade in DG-027: keeping
`npm run check` as the one command that checks everything was judged worth more than keeping the
image build free of the harness, and this repository's `npm ci` does not run install scripts, so no
browser is downloaded during a build.

It is still a coupling nobody chose on purpose, and it is the kind that is only noticed when a
release is blocked by a test dependency.

Found while adding the journeys, when the first image build failed on a type error in a test file.
Not fixed on the spot because both ways out — dropping the reference and type-checking the journeys
in their own script, or excluding `e2e/` from the image's build context — change what a documented
build contract command covers, and TECHNICAL-BASELINE is where that is decided.

Fix: decide whether `npm run check` or the image build is the one that should be narrow, then make
`TECHNICAL-BASELINE.md`'s Core build contracts say so.

### ADR 04's Core scope callout describes assignment that Core does not implement

`docs/adr/04-define-courier-recommendation-and-assignment.md:13` says Core "uses atomic Direct
Assignment **from the nearest-three recommendation**". It does not. `Dispatches.assign` revalidates
eligibility only, and `DispatchApiTest.assignmentRevalidatesEligibilityWithoutRequiringTheCourierToRemainInTheNearestThree`
deliberately pins that.

The decision was deliberate and is explained in PR #16 — which is the problem. `AGENTS.md` says each
ADR's callout names what Core actually implements, so a reader who trusts the callout is misled, and
the correction lives somewhere they will not look.

Found during DG-024 handoff review. Not fixed there because a callout amendment is its own docs PR
and DG-024 had no business editing ADR 04.

Fix: amend the callout to say Core assigns any currently Eligible Courier with eligibility
revalidated at assignment time, and that ADR 04's structured Recommendation Override reason stays
with Future Work 16. Leave the rest of the ADR alone; its preserved full-product design is intended.

### Recipient-facing times are shown in the reader's time zone, not the Handoff Address's

CONTEXT's **Delivery Time Zone** is "the Handoff Address's time zone, used for every Recipient-facing
Delivery time regardless of the viewing device's current zone", and ADR 05 adds that every public
time "includes its abbreviation". `web/src/track/copy.ts` calls `toLocaleString()`, which is the
reader's own zone and carries no abbreviation. A Recipient who has travelled — exactly the person a
delivery is being sent to at an address they are not currently at — is told the handoff happened at
a time that is right for their phone and wrong for their doorstep.

Closing it needs a time zone for a coordinate, which means a `tzdata` lookup keyed by latitude and
longitude. TECHNICAL-BASELINE lists external geocoding calls under "Explicitly absent from Core", and
a bundled boundary dataset is a dependency of the same size, so DG-025 could not close it either
way — it renders the correct instant in the wrong zone rather than inventing one.

Found while implementing DG-025's Delivered and Cancelled views. Recorded here rather than left in a
source comment, because a comment inside `formatTime` is not somewhere anyone goes looking for the
gap between the ADR and the product.

Fix: decide whether Core carries a coordinate-to-time-zone dataset at all. If not, amend ADR 05's
callout and CONTEXT's Delivery Time Zone to say Core shows the reader's zone, so the glossary stops
describing something the product does not do.

### A Dispatcher cannot copy a Tracking Link without leaving the application

`POST /api/deliveries/{id}/tracking-link/copy` exists, is Dispatcher-only and is tested, but no
button anywhere in `web/src/pages/DeliveryDetailPage.tsx` calls it. The only way a human reaches a
Tracking Link today is a `curl` session or the browser console, which is why the README's Recipient
demo path has to describe one.

DG-024 specified the endpoint and said nothing about a control; DG-025 is scoped to the Recipient's
side of the link. So the button belongs to neither, and the queue takes no additions.

Found while writing DG-025's demo path, when there turned out to be nothing to press. Not fixed
there because a new control in the Dispatcher workspace is new product behaviour, which
`ISSUE-WORKFLOW.md` says returns to refinement rather than silently expanding a PR.

Fix: one button on the Delivery detail page that calls the endpoint and writes the returned URL to
the clipboard, showing the expiry the response already carries. It must not render the URL into the
DOM or the page history — the response is the one place in the application that holds a raw
capability.

### The bundled application's static assets have no cache policy

`web/dist/assets/*` is content-hashed and could be cached for a year; `index.html` must not be
cached at all. Spring Boot is told neither, so both fall to heuristic caching, and a browser may
serve a stale `index.html` pointing at hashed chunks that no longer exist.

DG-025 hit the same problem for the two Recipient application files it introduces and fixed it for
those alone, in `RecipientApplicationHeadersFilter` — deliberately narrowly, because the two files
it names cannot carry a hash and the rest can.

Found while debugging a stale `/track-app.css` during the DG-025 Compose demo. Not fixed there
because the internal application's caching is not this Issue's surface and changing it would alter
how every Dispatcher and Courier page is delivered.

Fix: `spring.web.resources.cache.cachecontrol` for the hashed asset path plus an explicit `no-cache`
on the SPA shell, and confirm the Compose image serves both.

### Expired Tracking grants are never deleted

`tracking_grant` is only ever inserted into. `TrackingLinkRepository` has an insert and a lookup by
verifier and nothing else, and no sweeper anywhere touches the table — so every /track open a
Recipient ever performs leaves a row that outlives its own `expires_at`, the link, and the Delivery.
Nothing serves a stale row: `TrackingLinks.authorizedDeliveryForVerifier` rechecks the expiry on
every read. What accumulates is storage, and a permanent record of how often each link was opened,
which is a fact ADR 06 keeps out of the copy table on purpose.

Found while adding DG-026's stream recheck, which reads the same table on a heartbeat and made the
insert-only lifecycle obvious. Not fixed there because a retention rule for security-adjacent rows is
a decision rather than an implementation detail — ADR 06 defers security-event retention to Future
Work — and DG-026 had no business making it.

Fix: decide the retention rule, then either sweep `tracking_grant` rows past their `expires_at` on
the schedule `ExpiredLocationSweeper` already establishes, or record in ADR 06 that grants are kept
and say what for.

### A unit test names the collaborators of a service it is not testing

`server/src/test/java/com/deliveryglance/location/LocationSharingDispatchPositionTest.java:32`
constructs `LocationSharing` with `null` in the two positions the read under test does not reach.
It is honest about that in a comment, but it means every collaborator ever added to the service
breaks a test about a coordinate read — DG-022 wrote it with one `null`, DG-026 made it two, and the
edit is pure noise in both diffs.

Found when DG-026 added a fourth constructor argument and this test stopped compiling. Not fixed
there because the real fix is a seam decision — the freshness-filtered read is arguably its own
object — and inventing one from inside an SSE Issue is how speculative abstractions get in.

Fix: either give the dispatch/tracking position read a home that can be constructed on its own, or
accept the coupling and drive the test through the module's public interface with the whole context,
as `LocationPrivacyTest` already does.

### One type-aware lint warning predates the rule that reports it

`web/src/pages/DeliveryDetailPage.test.tsx:119` trips `typescript(no-base-to-string)`: a
`RequestInfo | URL` union is stringified, and a `Request` would become `"[object Request]"` and match
no path, so the assertion could pass or fail for the wrong reason.

PR #17 introduced the rule and fixed five of these with `urlOf`/`requestBodyStringOf`; this one
survived. It is a warning, so CI stays green and it is easy to stop seeing.

Found while reading lint output during DG-024. Not fixed there because touching a DG-021 test from a
DG-024 PR makes the diff harder to review than the warning is worth.

Fix: route the value through the existing `urlOf` helper in `web/src/testing/support.tsx`.

## Recently cleared

*(Nothing. Entries move out of this file by being deleted, not by being marked done.)*
