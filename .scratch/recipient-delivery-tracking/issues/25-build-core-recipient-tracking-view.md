# DG-025 · Build the Core Recipient tracking view

Type: implementation
Status: blocked
Labels: blocked, core, sprint-3, frontend, full-stack
Blocked by: DG-024
Estimate: 3–4 focused hours

## Outcome

A valid Link Holder gets a mobile-first, privacy-reduced view of current Delivery truth: state, next step, Handoff Address, limited Courier name and—only In Transit—one current marker with honest freshness.

## Read first

- [Core technical baseline](../implementation/TECHNICAL-BASELINE.md)
- [Ticket 12: Core Recipient view](12-rescope-to-resume-ready-core.md#tracking-link-and-recipient-view)
- [Core flow logic prototype](../prototypes/delivery-glance-core-flow-logic-prototype.html)
- [Lean roadmap variant A](../prototypes/delivery-glance-lean-roadmap-prototype.html?variant=A)
- [Full-product Recipient prototype](../prototypes/recipient-tracking-ui-prototype.html?variant=A&state=in_transit_live) — visual hierarchy only; its ETA, Running Late and detailed timeline are Future Work

## In scope

Introduce `recipientview` with one authorized snapshot query. It builds a purpose-specific DTO; it never serializes a Delivery aggregate and relies on the Tracking grant for scope.

| Delivery state | Public Core content |
|---|---|
| Awaiting Courier | Reference, Handoff Address, “Finding a courier”, next step; no Courier or map |
| Assigned | Reference, Handoff Address, limited Courier Display Name, “Courier heading to pickup”, next step; no Courier location/map |
| In Transit | Reference, Handoff Address, limited Courier Display Name, current state/next step and map/freshness rules below |
| Delivered | Reference, Handoff Address and actual handoff time; remove Courier identity and all location immediately |
| Cancelled | Generic cancelled result/time and configured Delivery Team Contact; no Courier, location or internal reason |

Map rules:

- Bundle MapLibre behind one `DeliveryMap` component. Production style URL is runtime configuration; tests use a local no-network style/substitute. Never put a Tracking token, Delivery ID or exact marker coordinates in tile URLs.
- Show Handoff and Courier markers only In Transit. The Courier marker is Live through 30 seconds, Delayed with explicit age through two minutes, then removed as Unavailable. Stop/permission withdrawal removes it immediately.
- Accuracy is represented honestly; never animate or extrapolate between reports. The browser's visible timer enforces expiry even with no server event.
- If production tile configuration is absent, preserve status/freshness content and show an honest map-unavailable state rather than failing the page.

Build loading, unavailable-link, connection-independent, map-unavailable and terminal states with keyboard/focus/contrast/mobile behaviour. Use state-derived copy; do not expose pickup address, internal account identity, assignment history, raw accuracy detail not needed by the UI or any operational reason.

## Acceptance criteria

- Projection tests cover every Core state and assert both allowed and forbidden fields.
- Fake-clock tests cover 30-second Live, two-minute Delayed/Unavailable and immediate Stop/terminal removal.
- Component tests cover valid states, invalid/unavailable link, map unavailable, keyboard navigation and a narrow mobile viewport.
- Browser/network tests prove the map is never loaded before successful link bootstrap and tile requests contain no tracking secret or Delivery identifier.
- A manually driven three-role Compose demo shows Awaiting, Assigned, In Transit with Live/Delayed/Unavailable, Delivered and Cancelled views.
- No ETA, Running Late, detailed Recipient Timeline or route/history line appears.

## Non-goals

- ETA, route line, navigation, proof of delivery, chat, rescheduling or Recipient actions.
- Full Tracking Link recovery/history and internal Matching/Reassignment details.
- Automatic updates; DG-026 adds SSE. This Issue may refetch on reload only.

## PR evidence

Include state-matrix test output and mobile screenshots for Awaiting, In Transit Live/Unavailable and Delivered. After merge, promote only DG-026.
