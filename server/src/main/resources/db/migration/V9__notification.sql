-- Off-band Recipient notification (Issue 51). Two tables: the channel a Recipient volunteered for
-- one Delivery, and the transactional outbox that decouples the send from the state-change command.
-- See ADR 13. No Courier coordinate ever reaches either table or the queue they feed.

-- The opt-in channel. Created and revoked only under a Tracking Link grant — the team never inserts
-- one — so a row existing here means a Recipient volunteered this channel for this Delivery. One per
-- Delivery: switching channel updates the row rather than adding a second. A NULL revoked_at is an
-- active subscription; setting it is the unsubscribe, and it is what silences even a queued message.
CREATE TABLE recipient_notification_subscription (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES delivery (id),
    channel VARCHAR(8) NOT NULL,
    -- The volunteered address: an email (up to the RFC ceiling) or an E.164 phone. Never the
    -- Recipient's name — the team learns a channel, not an identity.
    target VARCHAR(320) NOT NULL,
    consented_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT recipient_notification_channel_known CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT recipient_notification_target_present CHECK (btrim(target) <> '')
);

-- One subscription per Delivery. A re-opt-in is an update, not a new row.
CREATE UNIQUE INDEX recipient_notification_subscription_delivery_idx
    ON recipient_notification_subscription (delivery_id);

-- The outbox. One row is written in the same transaction as each notify-worthy transition, but only
-- when an active subscription exists — the write is an INSERT ... SELECT joined to the subscription,
-- so "no opt-in, no row" is enforced by the join rather than by a branch that could be forgotten.
-- transition_id is the delivery_transition it belongs to and the idempotency key: a redelivery
-- resolves the same row and finds it already sent.
CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    -- The transition this notifies about. Unique, so a transition can enqueue at most one message.
    transition_id UUID NOT NULL REFERENCES delivery_transition (id),
    delivery_id UUID NOT NULL REFERENCES delivery (id),
    next_state VARCHAR(16) NOT NULL,
    -- The public Delivery Reference, snapshotted so the message names the Delivery without a later
    -- read. ADR 05 makes the Reference public to a Link Holder; nothing else about the Delivery is
    -- copied here.
    delivery_reference VARCHAR(64) NOT NULL,
    -- The channel and target as they stood when the transition committed. The queue never carries
    -- these; the consumer reads them back through the begin callback, so a volunteered address is
    -- never placed in a broker.
    channel VARCHAR(8) NOT NULL,
    target VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    -- Set when the relay has handed the transition id to the queue. NULL means still to relay.
    published_at TIMESTAMPTZ,
    -- Set when the consumer has confirmed a provider send. Its presence is what makes a redelivery a
    -- no-op: begin returns ALREADY_SENT.
    sent_at TIMESTAMPTZ,
    -- Set when begin found the subscription revoked before a send happened. A suppressed row is
    -- terminal and never sends — the unsubscribe reached the message in time.
    suppressed_at TIMESTAMPTZ,
    CONSTRAINT notification_outbox_transition_unique UNIQUE (transition_id),
    CONSTRAINT notification_outbox_next_state_known
        CHECK (next_state IN ('ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT notification_outbox_channel_known CHECK (channel IN ('EMAIL', 'SMS')),
    -- A row is sent or suppressed, never both: the two terminal outcomes are exclusive.
    CONSTRAINT notification_outbox_one_outcome CHECK (sent_at IS NULL OR suppressed_at IS NULL)
);

-- The relay's read: unpublished, unsettled rows, oldest first. Partial so the index holds only work
-- still to do and empties as rows are relayed and sent.
CREATE INDEX notification_outbox_unpublished_idx
    ON notification_outbox (created_at)
    WHERE published_at IS NULL AND sent_at IS NULL AND suppressed_at IS NULL;
