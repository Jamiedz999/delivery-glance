# Delivery Glance

Delivery Glance lets a Recipient follow a live delivery without an account. A Dispatcher creates the
Delivery and assigns a Courier; the Courier shares their location and confirms handoff; the Recipient
watches through a link that expires.

This file is the glossary. It names the things in the product and nothing else — no implementation
detail, no progress, no plans. Where a name here differs from the name in the code, the entry says so.

## Scope

**Version 1**:
The finished release: everything named in this glossary is built and tested. Work beyond it is
tracked as open [GitHub Issues](https://github.com/Jamiedz999/delivery-glance/issues), not here.
_Avoid_: Core, MVP, phase one

## People

**Delivery Team**:
The one company that runs deliveries in this product. There is no second team and nothing to
administer.
_Avoid_: Fleet, tenant, marketplace

**Staff Account**:
A sign-in for a Dispatcher or a Courier, created in advance. There is no registration, no invitation
and no password reset.
_Avoid_: Internal Account, user account
_In code_: the `identityaccess` module

**Dispatcher**:
The person who creates a Delivery and assigns a Courier to it.
_Avoid_: Administrator, operator

**Courier**:
The person who collects the item, carries it, and hands it over.
_Avoid_: Driver, rider

**Courier Name**:
The name a Recipient sees while a Courier is carrying their Delivery. It is the only thing about the
Courier that a Recipient ever sees.
_Avoid_: Courier Display Name, courier profile

**Recipient**:
The person waiting for the delivery.
_Avoid_: Customer, user

**Support Contact**:
A phone number or email address shown to the Recipient after a Cancelled delivery, for questions
only. It does not let them reschedule, redirect or chat.
_Avoid_: Delivery Team Contact, support workflow

## The Delivery

**Delivery**:
One journey of one item, from pickup to handoff.
_Avoid_: Order, parcel, shipment

**Delivery Number**:
The identifier shown to the Recipient so they can tell one delivery from another. It reveals nothing
about them or the item.
_Avoid_: Delivery Reference, order number

**Pickup Address**:
Where the Courier collects the item. The Recipient never sees it.
_Avoid_: Origin, warehouse

**Delivery Address**:
Where the item is going. The Recipient sees this in full.
_Avoid_: Handoff Address, destination
_In code_: `handoffAddress`

**Active Delivery**:
A Delivery that is Assigned or In Transit. A Courier can have only one at a time.
_Avoid_: Open order, current job

**Status Change**:
A record that a Delivery moved from one state to the next, with the time and who did it.
_Avoid_: Delivery Transition, status edit

**Delivery Time Zone**:
The time zone of the Delivery Address. Every time shown to the Recipient uses it, whatever time zone
their phone is in.
_Avoid_: Browser time zone, UTC

## Delivery states

**Awaiting Courier**:
No Courier has been assigned yet. This is where every Delivery starts.
_Avoid_: Created, pending

**Assigned**:
A Courier is responsible for the Delivery but has not collected the item.
_Avoid_: Offered, accepted

**In Transit**:
The Courier has collected the item and is carrying it.
_Avoid_: Picked up, on route

**Delivered**:
The Courier confirmed the handoff. The Delivery is finished.
_Avoid_: Completed, closed

**Cancelled**:
A Dispatcher stopped the Delivery before pickup. The Delivery is finished.
_Avoid_: Deleted, failed

**Delivery Confirmation**:
The Courier tapping to confirm the handoff happened. Only this moves a Delivery to Delivered — being
near the Delivery Address does not.
_Avoid_: Handoff Confirmation, geofence completion

## Courier duty and location

**On Duty**:
The Courier says they are willing to take work. Only the Courier changes it, and it shares no
location by itself.
_Avoid_: Online, available

**Available Courier**:
An On Duty Courier with a recent location and no Active Delivery. Only these can be assigned.
_Avoid_: Eligible Courier, nearby courier

**Location Sharing**:
The Courier letting the app send their position. It starts only when they press Start — being On Duty
or having a Delivery is not enough — and it works only while the page is in front of them.
_Avoid_: Courier Location Sharing, automatic tracking, background tracking

**Location Sharing Session**:
One run of Location Sharing, from pressing Start until it stops. Signing out, closing the page or
reloading ends it; the Courier must press Start again.
_Avoid_: Browser permission, permanent consent

**Session ID**:
The identifier of one Location Sharing Session. Pressing Start again makes a new one, which is what
lets the server reject reports from the previous session.
_Avoid_: Location Sharing Generation, device identifier

**Session Key**:
A secret handed out once when a Location Sharing Session starts, held only by the page that started
it. The server stores a hash of it and never gives it out again, so a reloaded page cannot report for
a session it did not start.
_Avoid_: Reporting Secret, API key

**Sharing Paused**:
Location Sharing is still wanted but cannot produce a position right now — the page lost focus, the
network dropped, or the phone has no fix. It can recover on its own without pressing Start again.
_Avoid_: Location Sharing Interruption, stopped sharing

**Reporting Interval**:
Roughly ten seconds between position reports while Location Sharing is working. It is a target, not a
promise, and it never means positions are saved up and sent in a batch.
_Avoid_: Location Reporting Cadence, guaranteed timer

**Current Location**:
The newest usable position, judged by when the phone measured it — not by when the server received
it. A late report of an older position never replaces it.
_Avoid_: Latest-arrived location, estimated position

**Last Known Location**:
The most recent position reported, shown to a Recipient only while the Delivery is In Transit. It is
never guessed forward to where the Courier probably is now.
_Avoid_: Predicted position, route so far

**Location Accuracy**:
How large a circle the phone thinks the position could be in. A reading worse than one hundred metres
is shown but not used as the Current Location.
_Avoid_: Precision, exact position

**Location Age**:
How long ago a position was measured. Under thirty seconds it counts as Live; up to two minutes,
Delayed; past that, Unavailable.
_Avoid_: Location Freshness

**Live Location**:
A position measured in the last thirty seconds, shown as current.
_Avoid_: Any position older than that

**Delayed Location**:
A position between thirty seconds and two minutes old, shown with its age and without any animation.
_Avoid_: Live location, stale location

**Unavailable Location**:
No position exists, or the newest one is over two minutes old. The Courier marker disappears; the
time of the last report stays on screen.
_Avoid_: Frozen marker, unknown position

**Location History**:
A record of where a Courier has been over time. **This product never builds one** — positions live in
memory, one per Courier, overwritten and then deleted. It is named here so the promise has a name.
_Avoid_: Route History, breadcrumb trail

## Assignment

**Courier Recommendation**:
The three nearest Available Couriers for a Delivery, or all of them if there are fewer than three.
The Dispatcher sees a distance, never a position.
_Avoid_: Shortlist of winners, auto-assign

**Assignment**:
A Dispatcher picking one Courier from the Courier Recommendation. The database decides the race, so
two Dispatchers cannot assign the same Courier.
_Avoid_: Direct Assignment, claim, invitation

## The Tracking Link

**Tracking Link**:
A link that lets whoever holds it read one Delivery, with no account. Each Delivery has at most one
working link.
_Avoid_: Customer login, public tracking page

**Viewer**:
Whoever opens a Tracking Link. Usually the Recipient, but the link cannot prove that.
_Avoid_: Link Holder, authenticated recipient

**Tracking Session**:
What a Viewer gets after opening a valid Tracking Link: permission to read that one Delivery until
the link expires. It is not a Staff Account sign-in and reaches nothing else.
_Avoid_: Tracking Grant, recipient login
_In code_: `TrackingGrants`

**Tracking Link Expiry**:
When a Tracking Link stops working: seven days after it was made, or twenty-four hours after the
Delivery finished, whichever comes first. Opening it never extends this.
_Avoid_: Session timeout

**Tracking Link Lifecycle**:
The making, replacing, revoking and expiring of a Tracking Link. None of it changes the Delivery.
_Avoid_: Delivery status, delivery cancellation

**Copy Link**:
The Dispatcher copying the current Tracking Link to send through their own channel. It records who
copied it and when, and proves nothing about whether the Recipient got it.
_Avoid_: Tracking Link Copy, link delivered

**Replace Link**:
The Dispatcher swapping a Delivery's Tracking Link for a new one. The old link and any Tracking
Session on it stop working at once, and the expiry time does not move.
_Avoid_: Tracking Link Rotation, delivery reset

**Revoke Link**:
The Dispatcher switching off a Tracking Link with no replacement. The link and any Tracking Session
on it stop working at once, and the Delivery is untouched.
_Avoid_: Tracking Link Revocation, delivery cancellation

**Reissue Link**:
The Dispatcher making a fresh Tracking Link after the previous one expired or was revoked, while the
Delivery is still running. It starts a new expiry rather than reviving the old link.
_Avoid_: Tracking Link Reissue, automatic renewal

**Link Change Reason**:
The reason a Dispatcher must pick when they replace, revoke or reissue a link, with an optional
private note. The choices are Wrong Recipient, Suspected Exposure, Recipient Request, Access No
Longer Needed, Delivery Still Active and Other.
_Avoid_: Tracking Link Change Reason, free-text reason

**Link History**:
What the Dispatcher can see about a link: when it was made, copied, replaced, revoked, reissued or
expired, by whom, and why. It is not a record of the Recipient's browsing.
_Avoid_: Tracking Link History, recipient activity log

**Dead Link Page**:
The one page shown for any link that does not work — unknown, tampered with, expired or revoked. It
is identical in every case, so it cannot be used to find out whether a Delivery exists.
_Avoid_: Unavailable Link View, error page with a reason

**Finished Delivery Page**:
What a working Tracking Link shows after the Delivery ends, until the link expires. Delivered shows
the Delivery Number, Delivery Address, result and time; Cancelled shows the Delivery Number, a plain
result, the time and the Support Contact. Neither shows the Courier, a map or an ETA.
_Avoid_: Terminal Tracking View, delivery history

## What the Recipient sees

**Delivery Timeline**:
The list of milestones and their times on the Recipient's page. It shows what is true now, so a
reversed assignment puts it back to Awaiting Courier; it is not an audit log.
_Avoid_: Recipient Timeline, event stream

**What Happens Next**:
One sentence on the Recipient's page saying the next thing that will visibly happen. It never shows
what the Delivery Team is doing internally.
_Avoid_: Recipient Next Step, dispatcher note

**Live Connection**:
Whether the Recipient's page is still receiving updates by itself. It is shown separately from
Location Age — a page that is reconnecting still shows the facts it already has.
_Avoid_: Tracking Connection, courier online status

**Running Late**:
An active Delivery has passed the end of its ETA Window and is not Delivered yet. It stays visible
rather than being hidden by quietly moving the estimate.
_Avoid_: Failed delivery, automatic delay

## Arrival time

**ETA Window**:
A range of likely arrival times, rounded to five minutes, shown with when it was worked out. It is
about twenty minutes wide while Assigned and about ten while In Transit, and there is none before a
Courier is assigned.
_Avoid_: Exact arrival time, guaranteed time

**Travel Time**:
How long a journey takes, according to an outside service. It feeds the ETA Window and never shows a
route or gives directions.
_Avoid_: Travel-time Estimate, route plan, straight-line distance

**ETA Age**:
How long ago the ETA Window was last worked out. A failed lookup keeps the old window for up to five
minutes with its age shown; after that there is no ETA.
_Avoid_: ETA Freshness, silently reused ETA

## Proof and notifications

**Proof of Delivery**:
A photo and a signature the Courier can take at handoff. The image goes straight to private storage
and never through the app, which keeps only a reference to it.
_Avoid_: Delivery photo in the database, mandatory proof

**Proof Privacy**:
The Recipient is told only that proof was taken, never shown it. Location data is stripped from the
photo before anything is stored.
_Avoid_: Recipient-visible photo, retained photo location

**Delivery Notification**:
An email or SMS to the Recipient when their Delivery changes state, so they hear about it with no
page open. It is sent off to one side, so a slow provider never holds up a Courier or a Dispatcher.
_Avoid_: Off-band Notification, push notification

**Notification Opt-in**:
An email address or phone number the Recipient gives on the tracking page to get Delivery
Notifications about that one Delivery. It is the only way the Delivery Team ever holds a Recipient's
contact details, and the Recipient can take it back.
_Avoid_: Dispatcher-entered contact, mailing list

## What this product does not do

These are permanent boundaries, not a to-do list:

- Route planning, navigation and multi-stop optimisation
- Payments, billing and subscriptions
- Native mobile apps
- Letting a Recipient reschedule, redirect, cancel or chat
- Any interface that shows where a Courier has been (see **Location History**)
- Machine-learning arrival times
- Serving more than one Delivery Team
- Reporting: dashboards, heatmaps, courier performance
- Bulk import, export or editing of Deliveries
- Self-registration, password reset and managing team members
