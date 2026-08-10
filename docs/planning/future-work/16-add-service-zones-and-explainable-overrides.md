# Add Service Zones and explainable recommendation overrides

Type: future-work
Status: future
Milestone: Later Backlog
Blocked by: Core Acceptance from 12
Source decisions: 04, 10

## Outcome

Extend nearest-Courier recommendation with explicit geographic operating boundaries and explainable Dispatcher substitutions, while preserving eligibility as a non-overridable rule.

## Why deferred

Haversine ranking over fresh, On Duty, unassigned Couriers demonstrates the geospatial core with very little product surface. Polygon authoring/provisioning, containment edge cases, exclusion explanations and overrides are valuable later realism rather than MVP necessities.

## Scope

- Pre-provision a Service Zone polygon for each Courier; no self-service zone editor.
- Require both Pickup and Handoff points to be covered.
- Show exclusion counts and the current calculated-at time.
- Let a Dispatcher replace a recommended candidate only with another Eligible Courier and record the agreed structured override reason.
- Retain derived distances and rationale, never source coordinates, in the decision evidence.

## Acceptance

- Polygon boundary, overlap and invalid-geometry tests are deterministic.
- A Dispatcher can never override On Duty, location freshness, Active Delivery or Service Zone eligibility.
- Recommendation is revalidated before Assignment or a future Matching Round.
- In-memory JTS/Haversine remains the default unless profiling independently justifies PostGIS.

## Not included

Zone drawing UI, route coverage, pricing zones, multi-team territory administration or automatic territory optimisation.
