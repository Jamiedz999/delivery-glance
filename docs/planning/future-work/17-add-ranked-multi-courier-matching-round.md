# Add the ranked multi-Courier Matching Round

Type: future-work
Status: future
Milestone: Later Backlog
Blocked by: 15, 16 and Core Acceptance from 12
Source decisions: 03, 04, 09

## Outcome

Replace Core Direct Assignment with the fully designed consent-based Matching Round: invite up to three recommended Couriers, collect Interest for sixty seconds, then assign the highest-ranked still-eligible interested Courier rather than the fastest response.

## Why deferred

This is the largest business-logic branch in the original plan. It introduces timers, concurrent invitations, one active Interest per Courier, withdrawal, decline suppression, timeout cooldowns, round cancellation, reranking and detailed decision audit. None is needed to prove the Core database Assignment invariant.

## Scope

- Persist one active sixty-second Matching Round per Delivery.
- Invite the current confirmed shortlist without reserving Couriers.
- Support Match Interest, withdrawal and Decline; one Courier may hold Interest in only one round.
- At close, revalidate and rerank interested Couriers and atomically select the best remaining candidate.
- Add Timeout cooldown, Decline suppression, round cancellation and coordinate-free Recommendation Decision evidence.
- Keep Direct Assignment unavailable once this mode is enabled, avoiding two competing assignment policies.

## Acceptance

- Response speed never determines the winner.
- Overlapping rounds, duplicate responses and simultaneous closes still produce at most one Active Assignment per Courier and Delivery.
- Restart closes overdue rounds from persisted `closesAt` without inventing a result.
- Courier and Dispatcher prototypes cover Interest, withdrawal, timeout, no winner and successful selection.
- Recipient views reveal none of the internal matching activity.

## Not included

Marketplace bidding, price offers, ratings, broadcast-to-all, automated rebroadcast or post-pickup transfer.
