# Notify the Recipient off-band by capturing opt-in contact

Type: grilling
Status: resolved
Blocked by: 05, 06, 07

## Question

A Recipient learns nothing once their tracking page is closed: SSE is an in-page live view and
nothing reaches them off-band. Delivering an out-of-band notification on each state change forces a
decision the Core deliberately avoided — the team holding Recipient contact — and an execution shape
that must not block a Dispatcher or Courier command, must survive a crash between the state commit
and the send, and must never send the same notification twice. Whose contact is captured and how,
where the send is decoupled from the request path, and what a Recipient is told, all have to be
settled before the pipeline is built.

## Answer

> **Portfolio expansion, not Core.** [Ticket 07](07-set-core-and-expansion-boundaries.md) fixes that
> "Core stores no Recipient phone number or email address," and [Ticket 12](12-rescope-to-resume-ready-core.md)
> keeps Core to the three-Sprint MVP plus resume-ready surfaces. This decision belongs to the
> deliberate portfolio expansion set (Issue 51, paired with proof of delivery), which extends past
> Core's minimal scope on purpose. It reverses the no-contact stance **only under the narrow terms
> below**; Core itself, run without this feature configured, still holds no Recipient contact.

### The contact decision — opt-in from the tracking page

The team never records a Recipient's email or phone. Contact is **volunteered by the Recipient from
the tracking page itself**: while a valid Tracking Link is open, the Recipient may give one channel —
an email address or a phone number — and an explicit consent to be notified about *this* Delivery.
Nothing is stored against a Recipient until they choose to give it, so the privacy posture the team
operates under is unchanged: the team still cannot look up how to contact a person; it can only
deliver to a channel that person volunteered for one Delivery.

The rejected alternative is the Dispatcher recording contact at Delivery creation. It is simpler to
build, but it reverses the stance completely — the team would hold Recipient PII for every Delivery,
captured by staff rather than the person, with consent asserted on their behalf. Opt-in keeps the
capture where the consent is.

A subscription is scoped to one Delivery, carries the consent time, and has a revoke path. Authority
to create or revoke one is the **Tracking Link grant** and nothing else: the same capability that
authorizes reading the Delivery authorizes volunteering a channel for it. An Internal Account confers
no power to add, read, or change a subscription — the team cannot enrol a Recipient who did not
enrol themselves.

### The execution decision — transactional outbox, then an event pipeline

A state transition must not wait on a third-party email or SMS call, and the notification must
survive a crash between the database commit and the send. So the send is decoupled from the command
in the one place both facts are already true together — the transition's own transaction:

1. **Outbox in the same transaction.** Each notify-worthy transition — `ASSIGNED`, `IN_TRANSIT`,
   `DELIVERED`, `CANCELLED` — writes exactly one `notification_outbox` row alongside the transition,
   carrying the transition id, delivery id, next state, and the channel target snapshotted from the
   active subscription. A transition with no active subscription writes no row; that is how "a
   Recipient who never opted in is never contacted" holds by construction. Nothing sends inline.
2. **Relay.** A scheduled relay moves unpublished outbox rows to the queue and marks them published.
   The relay is at-least-once by design: a crash between the send and the mark re-sends, so the
   consumer, not the relay, owns exactly-once.
3. **Queue + Lambda.** A standard SQS queue with a dead-letter queue after a small redrive count
   delivers each message — carrying only the transition id — to a consumer Lambda. The queue carries
   no contact: the target stays in the database and is resolved by the callback below, so a volunteered
   channel never sits in a broker.
4. **Consumer, idempotent by transition id.** The Lambda calls the application back to *begin* the
   dispatch of a transition id. The application answers from the outbox row: `ALREADY_SENT` if it has
   a send recorded, `SUPPRESSED` if the subscription has since been revoked, otherwise `PROCEED` with
   the channel, target, and the state-derived message inputs. Only on `PROCEED` does the Lambda send —
   SES for email, SNS for SMS — and then call back to record the send. A redelivery of an already-sent
   transition returns `ALREADY_SENT` and sends nothing; a send that fails is left unrecorded, retried,
   and finally parked in the DLQ without ever touching the command that produced it.

This is the concrete consumer that [backlog #33](../planning/implementation/ISSUE-WORKFLOW.md) named
as the only trigger for a durable event backbone: an independent process (the Lambda) that cannot use
the Core transaction, consuming a non-coordinate domain event (a lifecycle transition), with an
outbox and idempotent-consumer failure design. The permanent prohibition still holds — **no Courier
coordinate ever enters the queue.** A transition id is not a position.

### Idempotency, precisely

Exactly-once *send* is guaranteed against **sequential redelivery**, which is the case SQS produces
in practice and the case the acceptance names: the first delivery sends and records `sent_at`; every
later delivery of the same transition id reads that record at *begin* and sends nothing. Two
deliveries of the same message running *concurrently* could both pass *begin* before either records —
a window a lease or a FIFO queue would close. Core's expansion does not add either; the window is
named here rather than hidden, and the standard production answer (a claim with a visibility lease, or
an SQS FIFO queue keyed by transition id) is recorded as the next step if concurrency is ever
observed.

### What the Recipient is told

The message is honest and derived from the state alone, reusing the same vocabulary the tracking page
already imposes on itself (`web/src/track/copy.ts` `STATE_COPY`). It reveals no dispatch internals —
no matching, declines, reassignment — exactly as [ADR 05](05-define-recipient-tracking-promise.md)
requires of the page. `CANCELLED` says the delivery was cancelled and names nothing further; it must
not imply a retry nobody arranged. Every message carries an unsubscribe path, and honouring it is the
`SUPPRESSED` answer above: a revoked subscription stops even a message already queued.

### Deployment shape

The outbox and relay are ordinary application code and run everywhere. The queue, DLQ, Lambda, SES
and SNS are new infrastructure, configured by deployment inputs (queue URL, region, credentials, a
shared callback token) exactly as proof of delivery's bucket is. A deployment that configures none of
it runs Core unchanged: transitions still write outbox rows if a subscription exists, but with no
relay target they simply accumulate, and no contact is ever sent. Locally the whole loop runs against
LocalStack (SQS + Lambda) with SES/SNS in their LocalStack form, which is the only way it runs without
an AWS account. Automated proof lives in the server's integration tests and the Lambda's pytest, not
in the Compose overlay.

### Non-goals

Push notifications or a mobile app; per-position "your courier is two minutes away" pings, which are
the SSE page's job; marketing or digest email; and storing Recipient contact the Recipient did not
volunteer. None of these are built, and the opt-in stance forbids the last outright.
