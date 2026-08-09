# Throwaway prototypes

These files exist only to answer Wayfinder design questions. They are not production implementation.

## Core flow logic — current implementation reference

Run:

    python3 -m http.server 4173 --directory .scratch/recipient-delivery-tracking/prototypes

Open:

    http://localhost:4173/delivery-glance-core-flow-logic-prototype.html

This is the current behavioural reference for implementation Issues 22–27. It isolates a pure state reducer from the DOM and provides both free-play controls and four guided scenarios:

- the three-role happy path;
- rejection of a second Assignment;
- Live → Delayed → Unavailable freshness plus immediate Stop privacy; and
- pre-pickup cancellation with an illegal later pickup blocked.

It intentionally has no Matching Round, ETA, Reassignment, Service Zone, Route History, database or production framework. Use the roadmap prototype for schedule/finished-product shape and this logic prototype for Core behaviour.

## Lean roadmap and finished-product plan

Run:

    python3 -m http.server 4173 --directory .scratch/recipient-delivery-tracking/prototypes

Open:

    http://localhost:4173/delivery-glance-lean-roadmap-prototype.html?variant=A

Use the bottom switcher or the left/right arrow keys to compare three presentations of the same authoritative plan:

- A — Outcome-first roadmap: recommended overview, scope reset, four Sprints, three product surfaces and resume checkpoint.
- B — Sprint delivery board: independently deployable outcomes, complexity budget and Future Work queue.
- C — Portfolio case study: narrative product, system, backlog and resume explanation.

The prototype shows the Sprint 3 MVP, Sprint 4 resume-ready Core and Future Work Issues 13–19. It is a planning visualisation, not production UI.

## Recipient tracking UI

Run:

    python3 -m http.server 4173 --directory .scratch/recipient-delivery-tracking/prototypes

Open:

    http://localhost:4173/recipient-tracking-ui-prototype.html?variant=A&state=in_transit_live

Use the bottom switcher or the left/right arrow keys to compare:

- A — Status story
- B — Map first
- C — Timeline first

The scenario selector covers the important Delivery, freshness, connection, terminal, and unavailable-link states. State is held only in the URL and memory.

Scope note: this file now carries a visible **Full product · Future Work included** ribbon. The selected Status Story layout remains the Portfolio Core direction, but its ETA/Running Late scenarios document [Future Work 13](../issues/13-add-travel-time-eta.md), not Sprint 3 scope. Implementation behaviour comes from the Core flow logic prototype above.

## Dispatcher and Courier workflow UI

Run the same server command:

    python3 -m http.server 4173 --directory .scratch/recipient-delivery-tracking/prototypes

Open:

    http://localhost:4173/dispatcher-courier-workflows-ui-prototype.html?variant=A&role=dispatcher&stage=matching_round

Use the bottom controls or the left/right arrow keys to compare:

- A — Focused workspaces: Dispatcher master/detail and Courier mobile home
- B — Operations board: lifecycle columns and a Courier Delivery deck
- C — Action path: one-next-decision console and Courier checklist

The role and workflow-stage selectors share the same mock business state across all variants. Buttons can drive a complete cross-role path from On Duty and Location Sharing through Matching, Assignment, pickup, location degradation/recovery, and a terminal outcome. The `State` control exposes the full in-memory state; nothing is persisted or sent to a backend.

Scope note: this file now carries a visible **Full product · Future Work included** ribbon. The focused workspace layout remains useful, but its Matching Round and exception flows document [Future Work 15](../issues/15-add-delivery-exceptions-and-reassignment.md) and [Future Work 17](../issues/17-add-ranked-multi-courier-matching-round.md). Portfolio Core uses Direct Assignment; implementation behaviour comes from the Core flow logic prototype above.
