-- Dispatcher Revocation: end access through the current Tracking Link without a replacement, and
-- give the link a lifecycle status the later recovery controls (Rotation, Reissue) build on.
--
-- Status is the fast check every read already reaches for: exchange, snapshot and heartbeat consult
-- the link row anyway, so a column there refuses a revoked link without a second query. revoked_at
-- lives beside it so "when did access end" needs no join; the applicable reason and internal note
-- are audit, and audit is a separate row.

ALTER TABLE tracking_link
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'active',
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD CONSTRAINT tracking_link_status_known CHECK (status IN ('active', 'revoked')),
    -- The timestamp exists exactly when the status says access ended, so neither can drift from the
    -- other: an active link has no revoked_at and a revoked one always does.
    ADD CONSTRAINT tracking_link_revoked_at_matches_status
        CHECK ((status = 'revoked') = (revoked_at IS NOT NULL));

-- The audit ADR 06 requires for Revocation: who ended access, when, the applicable reason and an
-- optional internal note. There is no token, URL or coordinate column, for the same reason the Copy
-- table has none — this record must never become something an internal identifier cannot replace.
--
-- DELIVERY_STILL_ACTIVE is absent from the reason CHECK on purpose: it is a reason to Reissue a link
-- the holder still needs, not to end access without a replacement, so it is not applicable here.
-- OTHER is only accepted with a note, so the history never records an unexplained revocation.
CREATE TABLE tracking_link_revocation (
    id UUID PRIMARY KEY,
    -- One revocation per link. Revocation is terminal in Core — there is no un-revoke and a revoked
    -- link is never revoked again — so this uniqueness also settles a Copy/Revocation race: the
    -- second writer to reach an already-revoked row is refused rather than recording a duplicate.
    link_id UUID NOT NULL UNIQUE REFERENCES tracking_link (link_id),
    actor_account_id UUID NOT NULL REFERENCES internal_account (id),
    reason VARCHAR(32) NOT NULL,
    note TEXT,
    revoked_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT tracking_link_revocation_reason_applicable CHECK (reason IN (
        'WRONG_RECIPIENT', 'SUSPECTED_EXPOSURE', 'RECIPIENT_REQUEST', 'ACCESS_NO_LONGER_NEEDED', 'OTHER')),
    CONSTRAINT tracking_link_revocation_other_needs_note
        CHECK (reason <> 'OTHER' OR (note IS NOT NULL AND btrim(note) <> ''))
);
