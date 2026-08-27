-- Proof of delivery (Issue 50): the immutable references to the artifacts a Courier captures at
-- handoff. This is the first table that points at binary content, and it stores none of it — only
-- object keys into the private S3 bucket, the content hash the processing Lambda computed, and the
-- two timestamps that bound an artifact's life. No image byte is ever written to PostgreSQL.
--
-- Lifecycle. A handoff attaches a row per captured artifact as PENDING, holding only the raw key.
-- The S3-triggered Lambda then validates the upload, strips EXIF/GPS, writes a cleaned copy and a
-- thumbnail, and calls back: a valid artifact becomes READY with its clean key, thumbnail key,
-- hash and processed time; anything that is not an image becomes REJECTED, quarantined and never
-- served. A processed row never changes again.

CREATE TABLE delivery_proof (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES delivery (id),
    kind VARCHAR(16) NOT NULL,
    -- Exactly what the browser uploaded, under raw/. Read only by the Lambda; never served.
    raw_object_key VARCHAR(512) NOT NULL,
    -- Filled by the Lambda's callback. NULL while PENDING, so a Dispatcher sees "processing" rather
    -- than a broken image, and always NULL for a REJECTED upload that has nothing safe to show.
    clean_object_key VARCHAR(512),
    thumbnail_object_key VARCHAR(512),
    content_hash CHAR(64),
    status VARCHAR(16) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    CONSTRAINT delivery_proof_kind_known CHECK (kind IN ('PHOTO', 'SIGNATURE')),
    CONSTRAINT delivery_proof_status_known CHECK (status IN ('PENDING', 'READY', 'REJECTED')),
    CONSTRAINT delivery_proof_raw_object_key_present CHECK (btrim(raw_object_key) <> ''),
    -- processed_at exists exactly when the Lambda has run, which is exactly when the row has left
    -- PENDING. The two facts cannot disagree.
    CONSTRAINT delivery_proof_processed_time_matches_status CHECK ((status = 'PENDING') = (processed_at IS NULL)),
    -- A READY proof is complete: it has a cleaned object, a thumbnail and a hash. A PENDING or
    -- REJECTED one has none of them, so no read path can reach an object that does not exist.
    CONSTRAINT delivery_proof_ready_is_complete CHECK (
        (status = 'READY') = (clean_object_key IS NOT NULL AND thumbnail_object_key IS NOT NULL
            AND content_hash IS NOT NULL))
);

-- One PHOTO and one SIGNATURE per Delivery: "one proof set per completed handoff". A retried
-- handoff attaches nothing new, and a second capture attempt is refused by the database.
CREATE UNIQUE INDEX delivery_proof_one_per_kind_idx ON delivery_proof (delivery_id, kind);
