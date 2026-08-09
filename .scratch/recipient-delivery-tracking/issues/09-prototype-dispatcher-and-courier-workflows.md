# Prototype the Dispatcher and Courier workflows

Type: prototype
Status: resolved
Blocked by: 04, 07, 11

## Question

What minimum Dispatcher and Courier screens and interactions support the agreed recommendation, confirmation, acknowledgement, status-update, and location-reporting loop without expanding into a general back-office or native-mobile product?

## Answer

> **Portfolio Core scope update:** The focused desktop/mobile workspace structure remains useful, but Core replaces the prototype's Matching Round with Direct Assignment and omits exception branches. The richer interactions are retained for [Future Work 15](15-add-delivery-exceptions-and-reassignment.md) and [Future Work 17](17-add-ranked-multi-courier-matching-round.md).

### Prototype

The throwaway, dependency-free [Dispatcher and Courier workflow prototype](../prototypes/dispatcher-courier-workflows-ui-prototype.html) runs with the one-command instructions in the [prototype README](../prototypes/README.md).

It renders the same business state through three structurally different variants on one shareable route:

- `?variant=A` — **Focused workspaces**: a desktop Dispatcher master/detail workspace and a single-column mobile Courier home.
- `?variant=B` — **Operations board**: Delivery lifecycle columns with a selected-detail drawer, paired with a Courier Delivery deck.
- `?variant=C` — **Action path**: a one-next-decision Dispatcher console and a Courier workflow checklist.

`role=dispatcher|courier` switches role without changing the selected Delivery state. `stage=` selects one of fifteen setup, Matching, Assignment, location, Reassignment, and terminal scenarios. The buttons also drive the in-memory state forward; `State` exposes the complete state after every action. No action reaches a backend or persists across reloads.

### Selected direction

Under the user's standing delegation to adopt the recommended option, **Variant A — Focused workspaces** is selected.

It gives each role the context and density appropriate to its device without creating two different products:

- the Dispatcher gets a desktop-first Delivery list beside the selected Delivery, with the current decision and its evidence visible together;
- the Courier gets one mobile-first home surface where availability, Location Sharing, invitations, and the Active Delivery cannot be confused with one another; and
- both roles see one state-dependent primary action while secondary or destructive actions remain available with explicit confirmation.

This direction scales across the entire lifecycle without making a map, aggregate dashboard, or activity feed the product's organising principle. It also uses the same Delivery detail to host recommendation, Matching Round, Assignment, location health, and terminal truth, avoiding a collection of one-off operational pages.

### Minimum Dispatcher surface

After sign-in, Core needs one Delivery workspace rather than a general back-office:

1. **Delivery index** — exact/partial Delivery Reference search, lifecycle-state filter, selected-row context, and `New Delivery`.
2. **Create Delivery panel** — Delivery Reference, Pickup Address, and Handoff Address only; successful submission opens the new `AWAITING_COURIER` detail.
3. **Delivery detail** — canonical state, pickup/handoff facts, Delivery Transition history, Tracking Link Copy, and one state-derived action area.
4. **Embedded recommendation and Matching panel** — calculation time, ranked Eligible Couriers, ranking inputs, exclusion counts, optional reasoned Recommendation Override, round countdown, responses, cancellation, and final outcome.
5. **State-specific confirmation panels** — pre-pickup Cancellation or Dispatcher Revocation with structured reason; these controls disappear after pickup.

The same detail becomes read-mostly after pickup: it shows the assigned Courier and current reporting health, but offers no route, navigation, post-pickup Reassignment, or free-form lifecycle edit. Terminal detail retains the durable transition result and history without reviving an action.

There is no standalone Courier directory, Recipient CRM, map dashboard, analytics area, bulk tooling, or role-administration surface.

### Minimum Courier surface

After sign-in, Core needs one mobile Web home rather than a native-style application hierarchy:

1. **Persistent operating controls** — On Duty and Location Sharing are separate, plainly labelled controls. Location copy states that the page must remain foregrounded and no Route History is created.
2. **Current action card** — mutually exclusive ready, Match Invitation, Match Interest, Assignment, In Transit, interrupted sharing, and terminal-result presentations.
3. **Invitation response** — `Match` and `Decline`, a visible sixty-second deadline, and copy explaining that response speed does not determine selection. Active Match Interest shows its binding-at-close meaning and allows withdrawal before close.
4. **Current Delivery** — Pickup and Handoff Addresses, state, provisional ETA when applicable, `Confirm pickup`, and pre-pickup Courier Withdrawal through a reasoned confirmation.
5. **In Transit outcomes** — Location Sharing state remains visible; `Delivered` requires Handoff Confirmation, while `Undeliverable` requires a structured reason and states that a later attempt is a new Delivery.

The home has no profile editing, task history, route trail, earnings, ratings, chat, navigation, or operating-system notification model. Multiple invitations can be reached from the same home, but only one Match Interest and one Active Delivery may exist at a time.

### Interaction conclusions

- On Duty never starts Location Sharing, and an Assignment never grants browser permission. On Duty without a usable fix remains visibly ineligible.
- Starting a Matching Round refreshes eligibility before sending; the displayed list is evidence, not stale authority.
- `Match` creates Interest rather than Assignment. Closing with no eligible Interest produces no winner; closing with an eligible Interest atomically selects the highest-ranked interested Courier.
- Going Off Duty during an Active Delivery changes neither the Assignment nor an existing sharing session. If that Off Duty Courier reaches a terminal outcome, sharing ends because no collection purpose remains.
- Reassignment is available only while Assigned, returns the Delivery to Awaiting Courier, suppresses the former Courier for that Delivery, and does not automatically start another round.
- Location Reporting interruption, explicit Stop, and recovery never edit Delivery state. Recovery sends only the newest position and the UI never draws a trail.
- Pickup, Delivered, Cancelled, and Undeliverable are explicit confirmations rather than proximity or free-form status controls.

### Alternatives rejected

Variant B makes lifecycle state exceptionally scannable, but it encourages an aggregate operations-dashboard identity, gives expendable counts undue prominence, and hides the recommendation evidence in a drawer. Its Courier deck also visually resembles a claim or swipe race despite corrective copy.

Variant C is the smallest-looking flow and makes the next action unmistakable, but its wizard-like sequence underrepresents parallel invitations and the need to inspect another Delivery while a Matching Round is active. The Dispatcher evidence column also separates too much Delivery context from the action.

Useful details retained from the alternatives are the visible no-race explanation from B and the one-next-action wording from C; they fit inside Variant A without adopting either layout.

### Verification and capture

The inline JavaScript passed syntax validation. A mocked DOM harness rendered all ninety combinations—three variants, two roles, and fifteen workflow stages—and checked that terminal views have no terminal action and In Transit Dispatcher views have no Reassignment action. It also verified that a round without Match Interest produces no winner, an eligible Interest produces one Assignment, Off Duty does not end an Active Delivery or sharing session, stopping and restarting sharing while Assigned does not advance the lifecycle, Courier Withdrawal returns the Delivery to Awaiting Courier without changing On Duty, terminal cleanup stops sharing when no purpose remains, and a complete cross-role action chain reaches Delivered.

The documented local server returned HTTP 200 for this prototype and the existing Recipient prototype. This workspace is not yet a Git repository, so the full variant set cannot be captured on the throwaway branch prescribed by the Prototype skill. It remains an explicitly named local scratch primary source and must be rewritten, not promoted directly, when production implementation begins.
