# Define the Recipient tracking promise

Type: grilling
Status: resolved
Blocked by: 03

## Question

Exactly what status, current-location freshness, ETA, next-step, and completion information does the Recipient see, how is each value derived, and how does the product communicate uncertainty rather than presenting stale or approximate data as live fact?

## Answer

> **Portfolio Core scope update:** [Ticket 12](12-rescope-to-resume-ready-core.md) retains state, Current Location, freshness and terminal privacy, but defers ETA/Running Late to [Future Work 13](13-add-travel-time-eta.md) and the detailed Recipient Timeline to Later Backlog.

### Public information by Delivery state

The Recipient page derives its headline and next step from the current canonical Delivery state rather than from free text:

| State | Headline and next step | Dynamic information |
|---|---|---|
| `AWAITING_COURIER` | “We’re preparing your delivery”; a Courier is being arranged | No Courier identity, map, location, or ETA |
| `ASSIGNED` | “A courier has been assigned”; the Courier will next collect the Delivery | Courier Display Name and a provisional ETA Window; no Courier location |
| `IN_TRANSIT` | “Your delivery is on the way”; the Courier is heading to handoff | Courier Display Name, map, Location Freshness, and updated ETA Window |
| `DELIVERED` | “Delivered” | Actual Handoff Confirmation time; no Courier marker or ETA |
| `CANCELLED` | “This delivery was cancelled” | Generic outcome and transition time |
| `UNDELIVERABLE` | “We couldn’t complete this delivery” | Generic outcome and transition time, with no implication of automatic retry |

The Recipient Timeline shows only these externally meaningful milestones and their Delivery Transition times. Matching, declines, timeouts, Recommendation Overrides, and Reassignment remain internal. If Reassignment changes `ASSIGNED` back to `AWAITING_COURIER`, the public view returns to preparing, removes the former Courier and ETA, and no longer presents the now-false assigned milestone as completed; internal history remains intact.

The open page receives automatic status, location, and ETA updates. It exposes a separate Tracking Connection state such as `Live updates` or `Reconnecting`, retains already received facts with their source timestamps during a disconnect, and offers retry without predicting changes. Core does not promise SMS, background, or operating-system push notifications.

### Public identity and privacy

A valid Tracking Link shows the Delivery Reference and full Handoff Address. While Assigned or In Transit it also shows a limited Courier Display Name. It does not expose the Recipient's name or phone number, item contents, full pickup address, or the Courier's phone number, photo, rating, legal identity, or location history.

### Map and Current Location

The map appears only while `IN_TRANSIT`. It shows Current Location with its accuracy radius and the Handoff Address, but no pickup marker, historical trail, planned route, navigation, or turn-by-turn instructions. The handoff marker remains when Courier location is unavailable; the Courier marker disappears immediately in a terminal state.

Current Location is the newest usable position by the Courier device's `recordedAt`, not by server receipt or page arrival. An out-of-order older reading never replaces a newer one. Each reading includes an accuracy radius; only readings accurate to within one hundred metres may replace Current Location. A poorer reading produces an explicit low-accuracy condition but does not move the marker or refresh its age.

Recipient-facing freshness is independent of the page's Tracking Connection:

- zero through thirty seconds old: Live Location, with live treatment;
- more than thirty seconds through two minutes old: Delayed Location, retained with `Updated X ago` but without live animation;
- more than two minutes old, or no usable reading: Unavailable Location, with the Courier marker removed and the last report time retained when known.

The system never extrapolates motion between reports or treats a newly received but old measurement as current.

### ETA Window

ETA uses external Travel-time Estimates without exposing a route or providing navigation. There is no machine-learning ETA, straight-line-speed fallback, or Dispatcher-entered estimate.

- `AWAITING_COURIER`: no ETA; explain that one becomes available after assignment.
- `ASSIGNED`: estimate Courier-to-pickup travel plus a fixed five-minute pickup buffer plus pickup-to-handoff travel. Present an approximately twenty-minute-wide provisional window.
- `IN_TRANSIT`: estimate Current Location to handoff travel. Present an approximately ten-minute-wide window.
- Terminal state: remove ETA; Delivered shows the actual handoff time.

Window endpoints round outward to five-minute boundaries. ETA is calculated immediately at Assignment and pickup, then at most once per minute while a usable Current Location exists. The page moves a window only when an endpoint changes by at least five minutes, preventing jitter, while `calculatedAt` continues to reflect successful recalculation.

If the travel-time service temporarily fails while location remains usable, the last successful window may remain for at most five minutes with `Last calculated X ago`. If Location becomes Unavailable, or no successful ETA exists within five minutes, remove the window and show `ETA temporarily unavailable`; recalculate automatically when valid inputs recover.

When an active Delivery passes the upper bound of its published window, show `Running later than expected` and attempt an immediate recalculation. A replacement window is explicitly labelled `ETA updated`; it never silently erases the missed estimate. If recalculation fails, retain the late message and show ETA as unavailable.

GPS proximity never changes the Delivery lifecycle. Only the current Courier's explicit Handoff Confirmation can transition `IN_TRANSIT` to `DELIVERED`.

### Completion, time, and help

Delivered shows the actual Handoff Confirmation time but Core includes no signature, photo, or other proof-of-delivery artifact. Cancelled and Undeliverable expose their generic public outcome and occurrence time, not internal reason codes or notes, and remove Courier location and ETA.

Every public time uses the Handoff Address's time zone and includes its abbreviation; an event outside the current local date also includes the date. Cancelled and Undeliverable pages offer a fixed Delivery Team phone number or email address for questions, but no chat, retry, rescheduling, cancellation, or address-change workflow.

Every dynamic value carries its own source or calculation time. Missing, delayed, low-accuracy, disconnected, unavailable, and late conditions are stated directly rather than hidden or presented with false precision.
