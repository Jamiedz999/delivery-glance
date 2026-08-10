# Delivery Glance

Delivery Glance is a recipient-first product for following an active last-mile delivery. Dispatch and courier operations support that recipient experience but do not define the product's primary identity.

This glossary names both the implemented Core language and intentionally preserved Later Backlog language. Defining a term here does not put it into Core; [Ticket 12](docs/planning/12-rescope-to-resume-ready-core.md) is the current scope boundary.

## Language

**Minimum Viable Product (MVP)**:
The first deployed end-to-end version in which a Dispatcher assigns a Courier, the Courier progresses and shares Current Location for a Delivery, and its Recipient follows through a Tracking Link. It proves the product loop but has not yet earned the evidence and presentation expected of Core.
_Avoid_: Prototype, finished portfolio project

**Core**:
The resume-ready release that strengthens the MVP with enough security, failure handling, automated evidence, documentation, and demo quality to present as completed portfolio work. It deliberately excludes optional business branches and scale components from the Later Backlog.
_Avoid_: Entire product roadmap, six-week bundle, MVP

**Core Acceptance**:
The evidence gate proving that Core's three-role happy path is repeatably deployable, protected at its important risks, usable on its target devices, and reproducible by another person from its documentation. Later Backlog work cannot begin before this gate passes.
_Avoid_: Happy-path demo, feature freeze

**Later Backlog**:
An ordered set of optional, independently valuable product or scale increments considered only after Core Acceptance. Its presence records possible direction but creates no delivery promise.
_Avoid_: Expansion Stage, committed Phase 2, unfinished Core

**Delivery Team**:
The single operational group whose Dispatchers coordinate and Couriers perform Deliveries in Core. It owns the configured Delivery Team Contact and pre-provisioned Internal Accounts; Core has no tenant or team-administration boundary.
_Avoid_: Fleet, tenant, marketplace

**Internal Account**:
A pre-provisioned identity for a Dispatcher or Courier in the single Delivery Team. Core includes sign-in but excludes self-registration, password recovery, invitations, and team-member administration; a Recipient never has an Internal Account.
_Avoid_: Recipient account, public registration

**Recipient**:
The person expecting a delivery and using the tracking experience to understand its current progress.
_Avoid_: Customer, user

**Delivery**:
A single fulfilment journey from pickup to handoff, with its own status, courier assignment, location, and—when the Later Backlog ETA increment is present—estimated arrival.
_Avoid_: Order, parcel, shipment

**Delivery Reference**:
A non-sensitive identifier shown through a Tracking Link so the Recipient can distinguish the Delivery without exposing their name, phone number, or item contents.
_Avoid_: Order number, Tracking Link token

**Pickup Address**:
The Delivery's internal origin, represented by a readable address and geographic point for eligibility and Travel-time Estimates. It is never exposed through a Tracking Link.
_Avoid_: Public pickup marker, Handoff Address

**Handoff Address**:
The Delivery's intended destination, shown in full through a valid Tracking Link. The Recipient's contact details, item contents, and full pickup address are not part of this public identity.
_Avoid_: Recipient profile, pickup location

**Courier**:
The person carrying out a Delivery and reporting its progress and current location.
_Avoid_: Driver, rider

**Courier Display Name**:
The limited public name shown to a Recipient while a Delivery is Assigned or In Transit. It excludes the Courier's phone number, photo, rating, legal identity, and location history.
_Avoid_: Courier profile, contact details

**On Duty**:
A Courier-declared condition indicating willingness to be considered for Delivery work. Only the Courier changes it; Direct Assignment, future matching outcomes, and future Reassignment do not implicitly take the Courier Off Duty.
_Avoid_: Online, available

**Courier Location Sharing**:
The Courier-authorized, foreground-only condition that permits current position reports for eligibility and Active Delivery tracking. It begins only through an explicit Start action, is not implied by On Duty or an Active Delivery, and may be ended by the Courier without changing the Delivery lifecycle.
_Avoid_: On Duty, automatic tracking, background tracking guarantee

**Location Sharing Session**:
The period of sharing intent created by one explicit Start action in the open Courier workspace. A temporary interruption may preserve that intent in the same page, but sign-out, close, or reload requires a new Start action.
_Avoid_: Browser permission, permanent consent

**Location Sharing Interruption**:
A temporary inability to produce position reports while a Location Sharing Session's intent remains, such as loss of foreground execution, connectivity, or a usable fix. It is shown separately from On Duty, Delivery state, and Location Freshness and may recover without another Start action while the same page remains open.
_Avoid_: Stop sharing, Courier Withdrawal, Tracking Connection

**Location Reporting Cadence**:
The approximately ten-second target between Courier position reports while Location Sharing is operating normally. It is not a freshness guarantee or permission to buffer a route: each report's measurement time still determines Location Freshness, and recovery submits only the newest available position.
_Avoid_: Recipient update latency, exact timer guarantee

**Service Zone**:
A geographic area in which a Courier agrees to perform Deliveries; an eligible Delivery's pickup and handoff points must both be covered.
_Avoid_: Route, radius

**Active Delivery**:
A Delivery in the Assigned or In Transit state. In Core, a Courier can have at most one Active Delivery.
_Avoid_: Open order, current job

**Eligible Courier**:
An On Duty Courier with fresh location data and no Active Delivery. A future Service Zone increment may additionally require coverage for both pickup and handoff, but distance ranking alone does not relax the current hard conditions.
_Avoid_: Online courier, nearby courier

**Tracking Link**:
A reusable bearer capability created with a Delivery that gives its holder read-only access to that Delivery without an account. Each Delivery has at most one active Tracking Link, which follows it through Reassignment; in Core, possession of that link is the sole access proof, with no additional PIN or Recipient account.
_Avoid_: Customer login, public tracking page, identity verification

**Link Holder**:
Any person or client presenting a valid Tracking Link, including but not necessarily the intended Recipient.
_Avoid_: Authenticated Recipient, account holder

**Tracking Link Expiry**:
The automatic end of Tracking Link access at the earlier of twenty-four hours after a terminal Delivery outcome or seven days after link creation. Opening or reusing the link never extends this time.
_Avoid_: Session timeout, first-open activation

**Tracking Link Lifecycle**:
The creation, validity, rotation, revocation, and expiry of read-only access, independent from the Delivery lifecycle. A Tracking Link change never transitions or cancels its Delivery.
_Avoid_: Delivery status, Delivery cancellation

**Tracking Link Rotation**:
The reasoned Dispatcher replacement of a Delivery's active Tracking Link, immediately invalidating the old link and any access established through it while making the replacement the Delivery's sole active link. It preserves the existing expiry rather than extending access and does not change the Delivery.
_Avoid_: Additional active link, Delivery reset

**Tracking Link Revocation**:
The reasoned Dispatcher ending of Tracking Link access without creating a replacement. It records a structured reason and optional internal note, immediately invalidating the link and any access established through it without changing the Delivery.
_Avoid_: Delivery cancellation, link expiry

**Tracking Link Reissue**:
The reasoned Dispatcher creation of a new Tracking Link when the previous link has expired or been revoked but its Delivery remains non-terminal. It creates a new validity period rather than restoring or extending the unavailable link; a terminal Delivery cannot be reissued a link.
_Avoid_: Automatic renewal, expired-link reactivation

**Tracking Link Copy**:
The Dispatcher action that copies the current Tracking Link for sharing through an existing external channel and records who copied it and when. It neither proves that a Recipient received the link nor creates, rotates, or extends one.
_Avoid_: Link delivered, Recipient notified

**Tracking Link Change Reason**:
The structured explanation required for Rotation, Revocation, or Reissue, with an optional internal note. Its shared vocabulary is Wrong Recipient, Suspected Exposure, Recipient Request, Access No Longer Needed, Delivery Still Active, and Other, filtered to the applicable action.
_Avoid_: Unstructured explanation, Delivery outcome

**Tracking Link History**:
The Dispatcher-visible record of link creation, copying, Rotation, Revocation, Reissue, and automatic expiry, including the responsible actor, time, and applicable Tracking Link Change Reason. It is distinct from security-only access evidence and is not a Recipient browsing history.
_Avoid_: Recipient activity log, Delivery lifecycle

**Unavailable Link View**:
The single data-free public response used for an unknown, invalid, expired, or revoked Tracking Link. It does not reveal whether a Delivery exists, its state, or why access is unavailable.
_Avoid_: Link-status diagnosis, Delivery lookup

**Terminal Tracking View**:
The privacy-reduced Tracking Link view available until expiry after a terminal outcome. Delivered shows the Delivery Reference, Handoff Address, result, and actual time; Cancelled or Undeliverable shows only a generic result, time, and Delivery Team Contact; no terminal view exposes Courier identity, location, map, or ETA.
_Avoid_: Live tracking, delivery history

**Recipient Timeline**:
A Recipient-facing view of meaningful Delivery milestones and their times, reflecting current public truth rather than serving as an immutable audit. It excludes Matching Round, Reassignment, and other internal dispatch activity, so a reversed Assignment returns the view to Awaiting Courier.
_Avoid_: Audit log, event stream

**Recipient Next Step**:
A state-derived sentence describing the next externally meaningful Delivery action without exposing internal dispatch activity or implying an unconfirmed retry.
_Avoid_: Dispatcher note, operational plan

**Last Known Location**:
The most recently reported Courier position, visible to a Recipient only while the Delivery is In Transit and never extrapolated into an unreported position. Its report time remains explicit whenever it is not live; ending permission or sharing withdraws its coordinates immediately but may leave that time visible.
_Avoid_: Current location when freshness is unknown, route history

**Route History**:
A chronological collection or presentation of past Courier positions. Neither Core nor any defined Later Backlog increment exposes it through a product interface; Current Location and Last Known Location are not Route History.
_Avoid_: Current Location, Recipient Timeline

**Current Location**:
The newest usable Courier position by its device-recorded measurement time, not by when a server or Recipient receives it. An older, delayed report never replaces it.
_Avoid_: Latest-arrived location, inferred position

**Location Accuracy**:
The reported radius of uncertainty around a Courier position. Only a report accurate to within one hundred metres can replace Current Location; a poorer reading is disclosed but not promoted as usable.
_Avoid_: Precision, exact position

**Live Location**:
A Last Known Location reported within the preceding thirty seconds and presented as current.
_Avoid_: Any position less than two minutes old

**Delayed Location**:
A Last Known Location reported between thirty seconds and two minutes ago, retained with its age but without live treatment or animation.
_Avoid_: Live location, stale location

**Unavailable Location**:
A Recipient-facing condition in which no Courier position exists or the last report is more than two minutes old. The Courier marker is absent while the last report time remains visible when known.
_Avoid_: Frozen live marker, unknown current position

**Tracking Connection**:
The Recipient page's ability to receive automatic updates, expressed separately from Location Freshness. A reconnecting page retains timestamped facts but does not imply that the Courier location itself is unavailable.
_Avoid_: Courier online status, location freshness

**ETA Window**:
A five-minute-rounded range of expected handoff times accompanied by its calculation time: approximately twenty minutes wide and provisional while Assigned, and approximately ten minutes wide while In Transit. It is absent while Awaiting Courier and replaced by the actual outcome time in a terminal state.
_Avoid_: Exact arrival time, guaranteed delivery time

**Travel-time Estimate**:
An external estimate of journey duration used as an ETA Window input without exposing a planned route or providing navigation. While Assigned it combines Courier-to-pickup travel, a fixed five-minute pickup buffer, and pickup-to-handoff travel; while In Transit it covers Current Location to handoff.
_Avoid_: Route plan, navigation instruction, straight-line ETA

**ETA Freshness**:
How recently an ETA Window was successfully recalculated from a usable Current Location. A failed travel-time request may retain the prior window for at most five minutes with its age disclosed; an Unavailable Location or older estimate makes ETA unavailable.
_Avoid_: Location Freshness, silently reused ETA

**Running Late**:
A Recipient-facing condition in which an active Delivery has passed the upper bound of its ETA Window without reaching Delivered. It remains explicit when a new window is calculated rather than being hidden by silently moving the estimate.
_Avoid_: Failed Delivery, automatic delay

**Delivery Time Zone**:
The Handoff Address's time zone, used for every Recipient-facing Delivery time regardless of the viewing device's current zone.
_Avoid_: Browser time zone, UTC display

**Delivery Team Contact**:
A fixed phone number or email address offered after a Cancelled or Undeliverable outcome for questions only, without creating chat, retry, rescheduling, or address-change capabilities.
_Avoid_: Support workflow, Recipient chat

**Dispatcher**:
The member of a Delivery Team who creates Deliveries and assigns an Eligible Courier from the ranked Courier Recommendation. A later matching increment may let the Dispatcher authorize a Matching Round instead.
_Avoid_: Administrator, operator

**Courier Recommendation**:
An ordered shortlist of three Eligible Couriers suitable for a Delivery, or all Eligible Couriers when fewer than three exist. It supports Core Direct Assignment and may later propose participants in a Matching Round, but the recommendation itself never assigns a Courier.
_Avoid_: Assignment, winner

**Direct Assignment**:
The Core assignment mode in which a Dispatcher selects one Eligible Courier from the current Courier Recommendation and the system atomically creates the Assignment after revalidation, without a Match Invitation or Match Interest.
_Avoid_: Match Selection, invitation, fastest response

**Recommendation Override**:
A Dispatcher's reasoned change to the recommended Matching Round shortlist. It requires a structured reason, may include an internal note, and never makes an ineligible Courier eligible.
_Avoid_: Forced assignment, eligibility override

**Matching Round**:
A Dispatcher-authorized, sixty-second attempt to find a Courier for one Delivery by inviting up to three top-ranked Eligible Couriers; only one can be active for a Delivery. At its close, current eligibility and the established ranking policy determine the winner rather than response speed.
_Avoid_: First-come-first-served dispatch, claim race

**Match Invitation**:
A request sent to a Courier to participate in a Matching Round. Receiving it neither reserves the Courier nor assigns the Delivery.
_Avoid_: Assignment Invitation, assignment, job blast

**Match Interest**:
A Courier's willingness to carry a Delivery if selected at the end of its Matching Round; it may be withdrawn until the round closes and is then binding. A Courier can hold Match Interest in only one round at a time and is temporarily reserved by it, but is not yet assigned.
_Avoid_: Acceptance, claim

**Match Decline**:
A Courier's explicit refusal of one Delivery, suppressing further invitations for that Delivery unless a Dispatcher restores invitation permission with a reason. It does not take the Courier Off Duty or change eligibility for other Deliveries.
_Avoid_: Going offline, timeout

**Match Timeout**:
The absence of a Courier response before a Matching Round closes, creating a five-minute invitation cooldown for that Delivery without taking the Courier Off Duty.
_Avoid_: Decline, rejection

**Match Selection**:
The single outcome of a Matching Round that revalidates and reranks interested Couriers, then assigns the highest-ranked Courier who remains eligible without requiring another acceptance.
_Avoid_: Fastest response, random selection

**Matching Round Cancellation**:
A Dispatcher's reasoned end to a Matching Round before selection, releasing every Match Interest without penalising or suppressing any invited Courier.
_Avoid_: Match Decline, Delivery cancellation

**Recommendation Decision**:
A timestamped account of one recommendation and Matching Round, including candidate order and rationale, exclusion counts, invitations, responses, closing eligibility and ranking, final outcome, cancellation, and any Recommendation Override. It retains only decision evidence, not raw Courier location history.
_Avoid_: GPS history, assignment result

**Reassignment**:
The reasoned pre-pickup end of an Assignment by Courier Withdrawal or Dispatcher Revocation. It returns the Delivery to Awaiting Courier, preserves assignment history, suppresses the former Courier from that Delivery unless a Dispatcher restores invitation permission with a reason, and does not automatically start another Matching Round.
_Avoid_: Transfer, replacement after pickup

**Courier Withdrawal**:
A Courier-initiated Reassignment before pickup, recorded with a Courier-specific structured reason and optional internal note. It does not implicitly change On Duty.
_Avoid_: Match Interest withdrawal, cancellation

**Dispatcher Revocation**:
A Dispatcher-initiated Reassignment before pickup, recorded with a Dispatcher-specific structured reason and optional internal note. It does not implicitly change the Courier's On Duty condition.
_Avoid_: Delivery cancellation, rejection

**Location Freshness**:
How recently a Courier location was reported; for Courier eligibility, only a report from the preceding two minutes is fresh. A stale report is never presented as live, and an explicit end to sharing or permission invalidates it immediately rather than waiting for its age threshold.
_Avoid_: Live location when the last update is unknown or stale

### Delivery lifecycle

**Awaiting Courier**:
The initial Delivery state in which no Courier has yet been selected to take responsibility for the Delivery.
_Avoid_: Created, waiting

**Assigned**:
A Delivery state in which a Courier has been atomically selected to take responsibility but has not yet collected the item. Core reaches it through Direct Assignment; a later matching increment may reach it through Match Selection.
_Avoid_: Offered, selected

**In Transit**:
A Delivery state that begins when the Courier confirms pickup and ends with handoff or an undeliverable outcome.
_Avoid_: Picked up, delivering, on route

**Delivered**:
The terminal Delivery state in which handoff to the Recipient has been confirmed.
_Avoid_: Completed, closed

**Cancelled**:
The terminal Delivery state in which a Dispatcher stops the Delivery before pickup.
_Avoid_: Deleted, failed

**Undeliverable**:
The terminal Delivery state in which an item that has been picked up cannot be handed to the Recipient. A later attempt is a new Delivery.
_Avoid_: Failed, retry pending

**Delivery Transition**:
A timestamped record that a Delivery moved from one lifecycle state to another, including the responsible actor and any applicable reason.
_Avoid_: Status edit, status overwrite

**Handoff Confirmation**:
The current Courier's explicit confirmation that the item was transferred successfully, which is required for the Delivered transition. GPS proximity to the Handoff Address is not confirmation.
_Avoid_: Geofence completion, arrival near destination
