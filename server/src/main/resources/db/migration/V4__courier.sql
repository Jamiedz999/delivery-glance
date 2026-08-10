-- On Duty is the only durable Courier fact in Core. It is the Courier's own declaration of
-- willingness to be considered for work, so it has to survive sign-out and restart, and nothing
-- else may change it.
--
-- A row appears the first time a Courier sets it. A Courier who has never touched it is Off Duty by
-- definition, so there is nothing to seed and no row to keep in step with internal_account.

CREATE TABLE courier (
    account_id UUID PRIMARY KEY REFERENCES internal_account (id),
    on_duty BOOLEAN NOT NULL,
    -- Only moves when the value actually changes, so it answers "since when" rather than
    -- "when was the button last pressed".
    on_duty_changed_at TIMESTAMPTZ NOT NULL
);
