-- A Delivery is the durable fulfilment record; delivery_transition is its append-only history.
-- Only the states this Issue can produce are allowed; later Issues widen the CHECK constraints
-- when they introduce assignment, pickup and handoff.

CREATE TABLE delivery (
    id UUID PRIMARY KEY,
    reference VARCHAR(64) NOT NULL,
    pickup_address_label VARCHAR(255) NOT NULL,
    pickup_latitude DOUBLE PRECISION NOT NULL,
    pickup_longitude DOUBLE PRECISION NOT NULL,
    handoff_address_label VARCHAR(255) NOT NULL,
    handoff_latitude DOUBLE PRECISION NOT NULL,
    handoff_longitude DOUBLE PRECISION NOT NULL,
    state VARCHAR(32) NOT NULL,
    version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT delivery_reference_unique UNIQUE (reference),
    CONSTRAINT delivery_reference_present CHECK (btrim(reference) <> ''),
    CONSTRAINT delivery_pickup_address_label_present CHECK (btrim(pickup_address_label) <> ''),
    CONSTRAINT delivery_handoff_address_label_present CHECK (btrim(handoff_address_label) <> ''),
    CONSTRAINT delivery_state_known CHECK (state IN ('AWAITING_COURIER', 'CANCELLED')),
    CONSTRAINT delivery_version_non_negative CHECK (version >= 0),
    CONSTRAINT delivery_pickup_latitude_wgs84 CHECK (pickup_latitude BETWEEN -90 AND 90),
    CONSTRAINT delivery_pickup_longitude_wgs84 CHECK (pickup_longitude BETWEEN -180 AND 180),
    CONSTRAINT delivery_handoff_latitude_wgs84 CHECK (handoff_latitude BETWEEN -90 AND 90),
    CONSTRAINT delivery_handoff_longitude_wgs84 CHECK (handoff_longitude BETWEEN -180 AND 180)
);

CREATE TABLE delivery_transition (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES delivery (id),
    previous_state VARCHAR(32),
    next_state VARCHAR(32) NOT NULL,
    actor_account_id UUID NOT NULL REFERENCES internal_account (id),
    -- Recorded as it was at the time: history should keep saying who acted even if the Internal
    -- Account's display name changes later, and reading it needs no join into identityaccess.
    actor_display_name VARCHAR(100) NOT NULL,
    reason_code VARCHAR(32),
    reason_note VARCHAR(500),
    -- The caller-supplied command identifier makes a retried command a no-op rather than a second
    -- transition. Creation carries no command identifier, and PostgreSQL allows repeated NULLs here.
    command_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT delivery_transition_command_unique UNIQUE (command_id),
    CONSTRAINT delivery_transition_states_differ CHECK (previous_state IS DISTINCT FROM next_state),
    CONSTRAINT delivery_transition_previous_state_known CHECK (previous_state IS NULL OR previous_state IN ('AWAITING_COURIER', 'CANCELLED')),
    CONSTRAINT delivery_transition_next_state_known CHECK (next_state IN ('AWAITING_COURIER', 'CANCELLED')),
    CONSTRAINT delivery_transition_reason_known CHECK (reason_code IS NULL OR reason_code IN ('NO_LONGER_REQUIRED', 'INVALID_DELIVERY_DETAILS', 'ITEM_UNAVAILABLE_AT_PICKUP', 'OTHER')),
    CONSTRAINT delivery_transition_other_reason_has_note CHECK (reason_code <> 'OTHER' OR btrim(coalesce(reason_note, '')) <> '')
);

CREATE INDEX delivery_transition_delivery_idx ON delivery_transition (delivery_id, occurred_at);
