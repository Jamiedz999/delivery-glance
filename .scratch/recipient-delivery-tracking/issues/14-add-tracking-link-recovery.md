# Add Tracking Link recovery and audit controls

Type: future-work
Status: future
Milestone: Later Backlog
Blocked by: Core Acceptance from 12
Source decisions: 06, 10

## Outcome

Let a Dispatcher recover from a wrongly shared or exposed Tracking Link without changing the Delivery, while keeping unavailable links indistinguishable and connected views immediately unauthorised.

## Why deferred

Core creation, repeatable Copy and automatic Expiry cover the happy path securely. Rotation, Revocation and Reissue add several command branches, reason policies, history UI and realtime invalidation cases that do not improve the first end-to-end demo.

## Scope

- Rotation replaces the current generation without extending its absolute expiry.
- Revocation ends access without replacement.
- Reissue creates a new link only for a non-terminal Delivery after Revocation or Expiry.
- Add the agreed structured reasons, optional internal notes and Dispatcher-visible Tracking Link History.
- Invalidate derived sessions and close connected Recipient SSE streams after commit.
- Retain only coordinate-free security evidence for the agreed short period.

## Acceptance

- Copy still returns the same current link and never rotates implicitly.
- Old tokens and established grants fail immediately after the committed action.
- Unknown, malformed, expired and revoked links still expose one identical data-free view.
- Raw capability material is absent from database dumps, logs, traces, analytics and error reports.
- Concurrent Copy/Rotation/Revocation tests produce one current generation and a complete audit.

## Not included

Recipient accounts, PINs, messaging delivery, identity verification or a Recipient browsing history.
