# Define the Tracking Link lifecycle

Type: grilling
Status: resolved
Blocked by: 03

## Question

When is a Tracking Link created, shared, activated, expired, or revoked; what data remains visible after completion or failure; and what threat model and recovery flow keep location access private without requiring a Recipient account?

## Answer

> **Portfolio Core scope update:** [Ticket 12](../planning/12-rescope-to-resume-ready-core.md) retains secure creation, repeatable Copy and automatic Expiry. Rotation, Revocation, Reissue and their history remain preserved here for [Future Work 14](../planning/future-work/14-add-tracking-link-recovery.md).

### Capability and threat boundary

A Tracking Link is a reusable, read-only bearer capability for exactly one Delivery. Possession authorizes access while the link is valid, but does not authenticate the holder as the intended Recipient. Core requires neither a Recipient account nor an additional PIN.

The design protects against guessing and enumeration, accidental leakage through search, logs, analytics, referrers, previews, or caches, stale access, and Dispatcher mistakes through rapid recovery. It does not claim to prevent deliberate forwarding, screenshots, or access through a compromised Recipient device or messaging channel. A Tracking Link can never modify a Delivery, Handoff Address, lifecycle state, or Assignment.

### Creation, copying, and use

The sole Tracking Link is created and becomes valid when its Delivery is created in `AWAITING_COURIER`. There is no first-open activation: `GET`, `HEAD`, link previews, scanners, and ordinary reads cannot consume the link, start a timer, or change any business state.

The Dispatcher uses `Copy Tracking Link` and distributes it through an existing external channel. Core stores no Recipient phone number or email address and sends no SMS or email; the Courier does not distribute the link. Copying records the Dispatcher and time but proves neither that a message was sent nor that the Recipient received it. Copying again returns the current link without rotating it or extending its validity.

One Delivery has at most one valid Tracking Link. The same link may be reopened and used concurrently on multiple devices, and viewing it never changes or extends its lifecycle. It remains scoped to the Delivery through Reassignment rather than to a particular Courier.

### Expiry and terminal views

The server-enforced expiry is the earlier of:

- seven days after the current link was issued; or
- twenty-four hours after the Delivery entered `DELIVERED`, `CANCELLED`, or `UNDELIVERABLE`.

Opening and reuse never extend either limit. A terminal outcome immediately removes Courier identity, map, location, and ETA while the valid link enters its remaining grace period:

- `DELIVERED` shows Delivery Reference, Handoff Address, the Delivered result, and actual Handoff Confirmation time.
- `CANCELLED` and `UNDELIVERABLE` show only a generic outcome, occurrence time, and Delivery Team Contact.

The Tracking Link lifecycle is independent of the Delivery lifecycle. Expiry, Rotation, Revocation, or Reissue never transitions or cancels the Delivery.

### Rotation, Revocation, and Reissue

- **Rotation** immediately invalidates the old capability and every access established through it, then creates the Delivery's sole replacement link. It preserves the old link's absolute expiry and therefore cannot bypass the seven-day limit.
- **Revocation** immediately invalidates the current capability and every access established through it, without creating a replacement.
- **Reissue** is an explicit Dispatcher recovery action after Expiry or Revocation, allowed only while the Delivery is non-terminal. It creates a completely new link and validity period under the same seven-day/terminal-plus-twenty-four-hour rule; it never revives an old link. Terminal Deliveries cannot receive a Reissue.

Each action requires one applicable structured reason from `WRONG_RECIPIENT`, `SUSPECTED_EXPOSURE`, `RECIPIENT_REQUEST`, `ACCESS_NO_LONGER_NEEDED`, `DELIVERY_STILL_ACTIVE`, or `OTHER`, and may carry an internal note. It records the Dispatcher and time.

An already-open page loses authorization immediately after Rotation or Revocation: automatic updates stop and sensitive content is removed. There is no refresh-based or timed grace period for derived access.

### Unavailable-link behaviour

Unknown, malformed, expired, and revoked capabilities are publicly indistinguishable. They expose no Delivery Reference, Handoff Address, Delivery existence or state, expiry time, revocation reason, or team-specific contact details. The fixed page says:

> This tracking link is no longer available. Contact the delivery team that shared it.

This unavailable view is distinct from a still-valid terminal grace-period view.

### Security acceptance requirements

- The capability is opaque, unpredictable, and contains no Delivery identifier, reference, state, time, or Recipient information.
- Raw capability material must not persist in databases, logs, analytics, error reports, or third-party scripts. No third-party analytics or session replay runs on the Tracking page.
- Every sensitive Tracking response uses protected transport, cannot be cached, and sends no referrer. Generic preview metadata exposes no Delivery data.
- Automated reads are side-effect free, and unknown, expired, and revoked links have data-free, externally indistinguishable behaviour.
- Rotation and Revocation invalidate any sessions and realtime connections derived from the old link.
- Invalid attempts are rate-limited and may trigger a challenge, alert, or investigation. Failed guesses never automatically revoke a valid link, which would allow denial of service.

The Core acceptance boundary is the security outcome, not a prematurely selected mechanism. Token bit length and encoding, digest or HMAC verification, fragment/cookie exchange versus path/query redaction, realtime invalidation, cache and telemetry configuration, rate-limit thresholds, CSP, map-provider isolation, and security-event retention belong to [Choose the Core technical architecture](10-choose-core-technical-architecture.md).

### Audit boundary

The Dispatcher-visible Tracking Link History contains creation, copying, Rotation, Revocation, Reissue, and automatic Expiry, with actor, time, and applicable reason. Separate security-only evidence captures first successful access establishment, reuse attempts against unavailable links, and suspected bulk guessing using an internal link identity rather than the raw capability. Neither record stores Courier location or every page refresh, poll, or realtime update, so it does not become Recipient browsing history.

The supporting primary-source review is [Tracking Link security research](../planning/research/tracking-link-security.md).
