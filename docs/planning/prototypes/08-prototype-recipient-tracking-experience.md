# Prototype the Recipient tracking experience

Type: prototype
Status: resolved
Blocked by: 02, 05, 06, 07

## Question

What page structure, information hierarchy, state transitions, and visual treatment let a Recipient understand status, location freshness, ETA, and next steps at a glance on a mobile browser across the important Delivery states?

## Answer

> **Portfolio Core scope update:** The selected Status Story hierarchy remains the Core direction, while ETA/Running Late and the detailed Timeline stay visible in this prototype as Future Work rather than first-release commitments. See [Ticket 12](../12-rescope-to-resume-ready-core.md).

### Prototype

The throwaway, dependency-free prototype is [Recipient tracking UI prototype](../prototypes/recipient-tracking-ui-prototype.html), with its one-command instructions in the [prototype README](../prototypes/README.md).

It provides three structurally different renderings on one shareable route:

- ?variant=A — **Status story**: current public truth and next step first, ETA next, conditional map, then details and timeline.
- ?variant=B — **Map first**: a full-viewport map or state canvas with status in a floating bottom sheet.
- ?variant=C — **Timeline first**: a denser document-like header, key facts, timeline, and map/detail columns.

The floating prototype bar cycles variants with buttons or left/right keys, selects a Delivery scenario, and exposes the complete in-memory public state. URL parameters preserve both the variant and scenario. Ten scenarios cover Awaiting Courier, Assigned, live and delayed In Transit, reconnecting with still-fresh location, unavailable location while late, Delivered, Cancelled, Undeliverable, and an unavailable Tracking Link.

### Selected direction

Under the user's standing instruction to adopt the recommended option without further choice questions, **Variant A — Status story** is selected.

It is the only structure that remains equally coherent when a map does not exist in AWAITING_COURIER, ASSIGNED, and terminal states. It answers the Recipient's questions in order:

1. What is happening?
2. What happens next?
3. When should it arrive?
4. Where is it, when location is legitimately visible?
5. Which public facts and milestones support that answer?

The mobile hierarchy is:

1. compact Delivery Glance identity and Delivery Reference when the state permits it;
2. separate lifecycle and Tracking Connection badges;
3. a large state-derived headline and Recipient Next Step;
4. a prominent ETA Window only when one exists, including freshness or provisional treatment;
5. a full-width map only while In Transit, with Location Freshness, report age, accuracy, handoff marker, and a Courier marker only when usable;
6. limited Courier and Delivery details; and
7. the current Recipient Timeline below the at-a-glance information.

Variant B is rejected as the base because its map dominance is strong only during In Transit and creates an empty decorative canvas in the other majority states; its bottom sheet also hides context behind scrolling. Variant C gives milestones useful weight but reads like an operational record, splits the current answer across regions, and is too dense for a Recipient's mobile glance.

### State and visual rules

- Live Location uses restrained green treatment and is the only state allowed to pulse.
- Delayed Location uses amber, retains the marker and report age, and never animates as live.
- Unavailable Location removes the Courier marker, retains the handoff marker and last report time, and removes ETA when its inputs are no longer usable.
- Reconnecting is a separate amber Tracking Connection label; it does not relabel a still-fresh Last Known Location as unavailable.
- Running Late becomes an explicit headline/notice and is never hidden by a silently moved ETA.
- Awaiting Courier has no Courier, map, location, or ETA; Assigned may show Courier Display Name and provisional ETA but no location.
- Delivered removes Courier, map, location, and ETA and retains only the permitted result, time, Delivery Reference, and Handoff Address.
- Cancelled and Undeliverable retain only the generic result, time, and Delivery Team Contact.
- Unknown, invalid, expired, and revoked links all use the same sparse, data-free unavailable page.
- Every variant uses English copy, the Handoff Address time zone, keyboard-visible focus, reduced-motion handling, and no third-party assets or analytics.

### Verification and capture

The documented server command returned HTTP 200, the inline JavaScript passed syntax validation, and all thirty variant/scenario combinations rendered in a headless DOM harness. Automated assertions confirmed that failed or unavailable-link views expose no Delivery Reference, Handoff Address, or Courier, and no terminal view renders ETA or Courier location.

This workspace is not yet a Git repository, so the prototype cannot be captured on the throwaway branch prescribed by the Prototype skill. It is therefore retained as an explicitly named local scratch primary source linked above; it must not be promoted directly into production code when the repository is later created.
