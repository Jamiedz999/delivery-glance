-- Internal Accounts are pre-provisioned for the single Delivery Team. Core has no registration,
-- invitation, password reset or account administration, so this table is only ever read at runtime
-- and seeded here. The demo values come from Flyway placeholders (see application.yml) and are
-- fictional; they are documented in README.md and .env.example.

CREATE TABLE internal_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT internal_account_email_unique UNIQUE (email),
    CONSTRAINT internal_account_email_normalised CHECK (email = lower(btrim(email))),
    CONSTRAINT internal_account_email_present CHECK (btrim(email) <> ''),
    CONSTRAINT internal_account_display_name_present CHECK (btrim(display_name) <> ''),
    CONSTRAINT internal_account_role_known CHECK (role IN ('DISPATCHER', 'COURIER')),
    -- Spring Security's delegating encoder needs the {id} prefix; a bare hash would be rejected.
    CONSTRAINT internal_account_password_hash_encoded CHECK (password_hash LIKE '{%}%')
);

INSERT INTO internal_account (email, password_hash, display_name, role)
VALUES ('${demoDispatcherEmail}', '${demoDispatcherPasswordHash}', '${demoDispatcherDisplayName}', 'DISPATCHER'),
       ('${demoCourierEmail}', '${demoCourierPasswordHash}', '${demoCourierDisplayName}', 'COURIER');
