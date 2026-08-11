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
