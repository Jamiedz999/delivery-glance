-- Direct Assignment is durable and remains as history after it stops being active. The two partial
-- unique indexes are the final arbiter when concurrent application checks race.

ALTER TABLE delivery DROP CONSTRAINT delivery_state_known;
ALTER TABLE delivery ADD CONSTRAINT delivery_state_known
    CHECK (state IN ('AWAITING_COURIER', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED'));

ALTER TABLE delivery_transition DROP CONSTRAINT delivery_transition_previous_state_known;
ALTER TABLE delivery_transition ADD CONSTRAINT delivery_transition_previous_state_known
    CHECK (previous_state IS NULL OR previous_state IN
        ('AWAITING_COURIER', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED'));

ALTER TABLE delivery_transition DROP CONSTRAINT delivery_transition_next_state_known;
ALTER TABLE delivery_transition ADD CONSTRAINT delivery_transition_next_state_known
    CHECK (next_state IN ('AWAITING_COURIER', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED'));

CREATE TABLE assignment (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES delivery (id),
    courier_account_id UUID NOT NULL REFERENCES internal_account (id),
    command_id UUID NOT NULL UNIQUE,
    assigned_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    CONSTRAINT assignment_end_not_before_start CHECK (ended_at IS NULL OR ended_at >= assigned_at)
);

CREATE UNIQUE INDEX assignment_one_active_delivery_idx
    ON assignment (delivery_id) WHERE ended_at IS NULL;

CREATE UNIQUE INDEX assignment_one_active_courier_idx
    ON assignment (courier_account_id) WHERE ended_at IS NULL;

CREATE INDEX assignment_courier_history_idx ON assignment (courier_account_id, assigned_at);
