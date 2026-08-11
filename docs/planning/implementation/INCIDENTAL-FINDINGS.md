# Incidental findings

Status: current

Things noticed while implementing something else. They are real, they are not the Issue that
found them, and the alternative to writing them here is a PR body nobody rereads — which is exactly
how ADR 04's callout below went stale for a Sprint.

This file is **not** a queue. `docs/planning/issues/` is the queue, it is fixed at DG-020…DG-028, and
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
