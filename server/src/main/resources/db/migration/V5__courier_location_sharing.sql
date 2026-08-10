-- One row per Courier: the Location Sharing Session currently allowed to report positions.
-- Starting a session replaces the row, and Stop, sign-out or a withdrawn browser permission deletes
-- it, which is what makes every reporting secret issued earlier useless immediately rather than
-- eventually.
--
-- There is deliberately no coordinate column here, and no report table anywhere in this schema:
-- positions exist only in process memory (see LatestLocationStore). A durable coordinate would be
-- the Route History the product promises never to keep, whatever it was called.

CREATE TABLE courier_location_sharing (
    courier_account_id UUID PRIMARY KEY REFERENCES internal_account (id),
    -- Names the session. A report naming any other generation is speaking for a session that no
    -- longer exists, and is refused rather than applied to the current one.
    generation UUID NOT NULL UNIQUE,
    -- SHA-256 of the reporting secret. The secret itself is returned once, to the browser that
    -- started the session, and is never stored, logged or returned again.
    reporting_secret_verifier VARCHAR(64) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT courier_location_sharing_verifier_is_sha256_hex
        CHECK (reporting_secret_verifier ~ '^[0-9a-f]{64}$')
);
