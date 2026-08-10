# Define Courier recommendation and assignment

Type: grilling
Status: resolved
Blocked by: 03

## Question

How should Courier eligibility, ranking, Dispatcher confirmation, Courier acknowledgement, timeout, rejection, and reassignment behave so the recommendation is explainable and cannot produce conflicting assignments?

## Answer

> **Portfolio Core scope update:** [Ticket 12](../planning/12-rescope-to-resume-ready-core.md) uses atomic Direct Assignment from the nearest-three recommendation. Service Zones/overrides and the full consent-based Matching Round remain preserved here for [Future Work 16](../planning/future-work/16-add-service-zones-and-explainable-overrides.md) and [Future Work 17](../planning/future-work/17-add-ranked-multi-courier-matching-round.md).

### Eligibility

A Courier is eligible for a Delivery only when all four conditions hold:

- the Courier is On Duty;
- the latest reported location is no more than two minutes old;
- the Courier has no Active Delivery, meaning no Delivery in `ASSIGNED` or `IN_TRANSIT`;
- the Courier's Service Zone covers both the pickup and handoff points.

Core permits at most one Active Delivery per Courier. Vehicle type, ratings, historical performance, and similar quality signals are not eligibility inputs. Eligibility is a hard boundary: neither ranking nor a Dispatcher can override it.

When no Courier is eligible, the Delivery remains `AWAITING_COURIER`. The Dispatcher sees exclusion counts grouped by reason, and the recommendation may be recomputed when conditions change; the system never silently relaxes a constraint.

### Ranking and recommendation

Eligible Couriers are ranked by distance from their latest reported position to the pickup point, ascending. Equal distances are resolved by longest idle time and then by a stable Courier identifier, producing a deterministic order.

A Courier Recommendation contains the first three ranked Couriers, or every Eligible Courier when fewer than three exist. It includes `calculatedAt`, the visible ranking inputs for each candidate, and exclusion counts. The system revalidates and refreshes it immediately before a Dispatcher starts a Matching Round rather than treating an old screen as authority.

The Dispatcher normally authorizes the recommended shortlist. The Dispatcher may replace a candidate only with another Eligible Courier and must record one structured Recommendation Override reason: `LOCAL_KNOWLEDGE`, `WORKLOAD_BALANCING`, `COURIER_REQUEST`, or `OTHER`; an internal note is optional.

### Matching Round

One Delivery can have at most one active Matching Round. A round lasts sixty seconds and sends a Match Invitation to the confirmed shortlist. Merely receiving an invitation neither reserves a Courier nor assigns the Delivery, so a Courier may view invitations from multiple Deliveries.

Clicking `Match` creates Match Interest in that Delivery and temporarily reserves the Courier to that round. A Courier can hold Match Interest in only one round at a time. The Courier may withdraw it until the round closes; an Interest still active at closing is binding consent to carry the Delivery if selected.

At closing, the system revalidates every interested Courier with current eligibility inputs and reranks the remaining candidates with the established policy. It selects the highest-ranked eligible interested Courier—not the fastest responder—and requires no second acceptance. Selection is conditional and atomic: the Delivery must still be `AWAITING_COURIER`, the round must still be active, and the Courier must still be free of an Active Delivery. A failed candidate is skipped in favour of the next valid candidate; at most one Courier and one Delivery can win.

A successful Match Selection transitions the Delivery to `ASSIGNED`. All temporary reservations are released, the winner is told that the Delivery was assigned, the other participants are told that another Courier was selected, and the Dispatcher receives the outcome summary.

### Decline, timeout, no match, and cancellation

- Match Decline is an explicit refusal of that Delivery. It suppresses later invitations for the same Courier–Delivery pair until a Dispatcher restores invitation permission with a recorded reason.
- Match Timeout means no response before the round closes. It creates a five-minute invitation cooldown for that Courier–Delivery pair.
- A Courier who withdraws Match Interest or is not selected receives no cooldown or penalty.
- Decline, timeout, and non-selection do not change the Courier's On Duty condition.
- If no eligible interested Courier remains at closing, the Delivery stays `AWAITING_COURIER`. Recommendations refresh, but a new Matching Round requires an explicit Dispatcher action; there is no automatic rebroadcast and no assignment without consent.

A Dispatcher may cancel an active round with `CANDIDATES_CHANGED`, `OPERATIONAL_PAUSE`, or `OTHER`; `OTHER` requires an internal note. Matching Round Cancellation invalidates late responses and releases all Match Interests without treating any Courier as having declined. Cancelling the Delivery also ends its active round.

### Reassignment before pickup

Reassignment is allowed only while the Delivery is `ASSIGNED`. Either action below returns it to `AWAITING_COURIER`, clears the current Courier, notifies both parties, preserves Assignment and Delivery Transition history, and does not automatically start another Matching Round:

- Courier Withdrawal: `NO_LONGER_AVAILABLE`, `CANNOT_REACH_PICKUP`, `VEHICLE_ISSUE`, or `OTHER`;
- Dispatcher Revocation: `COURIER_UNRESPONSIVE`, `OPERATIONAL_CHANGE`, `ASSIGNMENT_ERROR`, or `OTHER`.

`OTHER` requires an internal note; notes are optional for the other reasons. The former Courier is suppressed from further invitations for that Delivery unless a Dispatcher restores permission with a reason, but remains eligible for other Deliveries when the ordinary conditions hold. Neither action implicitly changes On Duty; that remains an explicit Courier decision. Reassignment remains forbidden after pickup.

### Explainability and audit

Each Recommendation Decision records the calculation time, ordered candidates and derived ranking inputs, exclusion counts, invited Couriers, response types and times, withdrawn Interests, closing eligibility and ranking, final selection or no-winner reason, Matching Round Cancellation, and any Recommendation Override. This evidence does not add a raw GPS history.

Matching activity and reassignment detail remain internal. They do not replace the Recipient-facing Delivery status or expose internal notes through the Tracking Link.
