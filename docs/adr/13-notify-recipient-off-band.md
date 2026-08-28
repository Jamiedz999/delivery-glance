# ADR 13 — The Recipient volunteers their own contact, and the send happens off to one side

## The question

How does a Recipient hear that their Delivery changed when no tracking page is open, without the
Delivery Team ever holding their contact details and without a slow email provider blocking a Courier?

## What we decided

### Where the contact comes from

**The Recipient gives it, from the tracking page.** While a valid Tracking Link is open they may
offer one channel — an email address or a phone number — and consent to be told about *that one
Delivery*. Authority to create or revoke it is the Tracking Session and nothing else: a Staff Account
cannot add, read or change one.

The alternative was the Dispatcher typing in a contact when creating the Delivery. Simpler to build,
and it reverses the whole stance: the team would hold contact details for every Recipient, captured
by staff, with consent asserted on their behalf.

### How the message gets out

The send never happens on the request thread. Instead:

1. **Outbox row, same transaction.** A notify-worthy state change — `ASSIGNED`, `IN_TRANSIT`,
   `DELIVERED`, `CANCELLED` — writes one `notification_outbox` row alongside the Status Change. No
   subscription means no row, which is how "a Recipient who never opted in is never contacted" holds
   by construction rather than by a check somebody could forget.
2. **Relay.** A scheduled job moves unpublished rows onto an SQS queue. It is at-least-once on
   purpose — a crash between sending and marking will re-send — so the consumer owns exactly-once.
3. **Queue carries no contact.** The message holds only a Status Change id. The channel stays in the
   database, so a volunteered phone number never sits in a broker.
4. **Consumer asks before sending.** The Lambda calls the application to begin dispatching that id.
   The answer is `ALREADY_SENT`, `SUPPRESSED` (the Recipient revoked), or `PROCEED` with the channel
   and the message. Only `PROCEED` sends; then it calls back to record the send.

## Why

Two failures drive this. A Courier tapping "delivered" must not wait on an email provider, and a
crash between the database commit and the send must not lose the message. Writing the intent in the
same transaction as the state change is the one place both facts are already true together.

Revoking works even on a message already queued, because the queue carries an id and not a message —
the consumer re-reads consent at send time.

**One honest limit.** Exactly-once holds against *sequential* redelivery, which is what SQS produces
in practice. Two deliveries of the same message running *concurrently* could both pass the begin call
before either records a send. A visibility lease or a FIFO queue keyed on the id would close that
window; neither is built. It is named here rather than hidden.

This is also the only trigger that would justify a durable event backbone
([#33](https://github.com/Jamiedz999/delivery-glance/issues/33)): an independent process that cannot
join the application's transaction, consuming a non-coordinate event. The prohibition still holds —
**no Courier coordinate ever enters the queue.** A Status Change id is not a position.

## What is built

The whole loop ([#51](https://github.com/Jamiedz999/delivery-glance/issues/51)). The outbox and relay
are ordinary application code and run everywhere. The queue, dead-letter queue, Lambda, SES and SNS
are deployment inputs; configure none of them and the application runs unchanged, writing outbox rows
that simply accumulate. Locally the loop runs against LocalStack.

The message is derived from the state alone and reuses the tracking page's own wording
(`web/src/track/copy.ts`). It reveals nothing about what the Delivery Team is doing internally, and
`CANCELLED` never implies a retry nobody arranged. Every message carries an unsubscribe path.

Not built, and not wanted: push notifications, a mobile app, "your courier is two minutes away"
pings, marketing email, and any contact the Recipient did not volunteer.
