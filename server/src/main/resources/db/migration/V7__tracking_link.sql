-- One Tracking Link per Delivery, created with the Delivery and never rotated in Core.
--
-- The raw capability is never here. It is rederived on demand as an HMAC over the link's own random
-- identity and generation under a deployment key, so Copy can hand out the same token twice without
-- anything having stored it. What is stored is the derivation input, which is useless on its own,
-- and a SHA-256 verifier, which proves a presented token is the right one but cannot produce it.
--
-- generation exists although Core never increments it. Rotation is Future Work 14, and the grant
-- table below already scopes access to one generation; leaving the column out would mean every
-- established grant survives a rotation that has no way to say which generation it invalidated.

CREATE TABLE tracking_link (
    delivery_id UUID PRIMARY KEY REFERENCES delivery (id),
    -- Random and unrelated to the Delivery. Deriving the capability from delivery_id would make
    -- every token a function of a value that appears in Dispatcher URLs and internal logs.
    link_id UUID NOT NULL UNIQUE,
    generation INTEGER NOT NULL,
    -- Which deployment key derived the current capability. A key rollover changes every token, so
    -- the version has to be recorded per link rather than assumed to be the configured one.
    key_version INTEGER NOT NULL,
    -- SHA-256 of the derived token, hex. Verification recomputes the token and compares digests in
    -- constant time, so a database copy cannot be turned back into a working link.
    token_verifier VARCHAR(64) NOT NULL UNIQUE,
    issued_at TIMESTAMPTZ NOT NULL,
    -- The seven-day limit, fixed at creation. The terminal-plus-24-hour limit is not stored: it is
    -- derived from delivery_transition, so a Delivery that reaches a terminal state cannot leave a
    -- stale expiry behind for a sweeper to notice later.
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT tracking_link_generation_positive CHECK (generation >= 1),
    CONSTRAINT tracking_link_key_version_positive CHECK (key_version >= 1),
    CONSTRAINT tracking_link_verifier_is_sha256_hex CHECK (token_verifier ~ '^[0-9a-f]{64}$'),
    CONSTRAINT tracking_link_expires_after_issue CHECK (expires_at > issued_at)
);

-- The narrowly scoped session a Link Holder gets by exchanging the fragment once. It authorizes
-- reading one Delivery through one link generation and nothing else; it is not an Internal Account
-- session and carries no role.
CREATE TABLE tracking_grant (
    id UUID PRIMARY KEY,
    link_id UUID NOT NULL REFERENCES tracking_link (link_id),
    -- The generation the grant was established through. Core never rotates, so this always equals
    -- the link's current generation; the comparison is what Future Work 14 needs to invalidate
    -- derived access without hunting down individual grants.
    generation INTEGER NOT NULL,
    -- SHA-256 of the cookie value, hex. Same reasoning as the link verifier: a stolen database row
    -- must not be usable as a session.
    secret_verifier VARCHAR(64) NOT NULL UNIQUE,
    established_at TIMESTAMPTZ NOT NULL,
    -- Bounded by the link's effective expiry at the moment of exchange, so a grant can never outlive
    -- the capability it came from.
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT tracking_grant_generation_positive CHECK (generation >= 1),
    CONSTRAINT tracking_grant_verifier_is_sha256_hex CHECK (secret_verifier ~ '^[0-9a-f]{64}$'),
    CONSTRAINT tracking_grant_expires_after_establishment CHECK (expires_at > established_at)
);

CREATE INDEX tracking_grant_link_idx ON tracking_grant (link_id);

-- Copy evidence, deliberately minimal: who copied and when. ADR 06's full Tracking Link History,
-- with Rotation, Revocation, Reissue and structured reasons, is Future Work 14. There is no token
-- column and no URL column here, because Copy is the one moment the raw token exists and this is
-- the table that would be tempted to keep it.
CREATE TABLE tracking_link_copy (
    id UUID PRIMARY KEY,
    link_id UUID NOT NULL REFERENCES tracking_link (link_id),
    actor_account_id UUID NOT NULL REFERENCES internal_account (id),
    copied_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX tracking_link_copy_link_idx ON tracking_link_copy (link_id, copied_at);
