# Add external travel-time ETA

Type: future-work
Status: future
Milestone: Later Backlog
Blocked by: Core Acceptance from 12
Source decisions: 05, 10

## Outcome

Add honest travel-time-based ETA Windows after the resume-ready Core is stable, without turning Delivery Glance into a route-planning or navigation product.

## Why deferred

Status plus Current Location already proves the Recipient value loop. ETA adds provider licensing, billing, quota, timeout and degradation work but is not required to demonstrate atomic Assignment, privacy-minimised GPS or SSE. Deferring it removes an external failure source from the MVP.

## Scope

- Use the existing `TravelTimePort` boundary and a contract-approved provider.
- While Assigned, estimate Courier → pickup → handoff plus the fixed pickup buffer.
- While In Transit, estimate Current Location → handoff at most once per minute.
- Present rounded provisional/current windows, calculation age, five-minute stale fallback and explicit unavailable state.
- Keep `Running Late` visible instead of silently moving a missed window.

## Acceptance

- Provider DTOs never enter the domain or Recipient response.
- No route geometry, polyline, navigation instruction or straight-line-speed fallback is stored or shown.
- Provider failure never rolls back a Delivery transition and never holds an Assignment lock.
- Contract tests cover success, timeout, rate limit, stale fallback and recovery.
- Provider terms, DPA/privacy disclosure, quota and billing cap are recorded before deployment.

## Not included

Machine-learning ETA, route optimisation, turn-by-turn navigation, driver guidance or route history.
