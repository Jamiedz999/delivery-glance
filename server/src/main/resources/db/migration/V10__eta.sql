-- External travel-time ETA (Issue 27). One table: the single current ETA Window per Delivery, a
-- projection replaced on each successful recalculation. See ADR 05 and ADR 10. No route geometry,
-- polyline, waypoint or Courier coordinate is ever stored here — only the two rounded endpoints and
-- when the provider last answered. A provider response is not a source of Delivery truth; this table
-- holds a derived window and nothing a lifecycle decision reads.

-- One row per Delivery, and only while a window exists. A row appearing means the last recalculation
-- succeeded and produced a window; the row being absent means there is no current ETA — never
-- computed, withdrawn because the Courier's location went unavailable, or the Delivery is terminal.
-- The endpoints are already rounded outward to five-minute boundaries when written, so a read never
-- has to round and can never show a false-precision minute.
CREATE TABLE delivery_eta (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES delivery (id),
    -- The published window: earliest and latest estimated handoff. window_start <= window_end always.
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    -- When the provider last answered successfully. The browser ages this to decide when the window
    -- is stale ("last calculated X ago") and when, past five minutes, it must be withdrawn — so a
    -- recalculation that did not move the endpoints still advances this to keep the window fresh.
    calculated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT delivery_eta_window_ordered CHECK (window_start <= window_end)
);

-- One current ETA per Delivery. A recalculation updates this row rather than adding a second, so
-- there is never a stale window competing with the live one.
CREATE UNIQUE INDEX delivery_eta_delivery_idx ON delivery_eta (delivery_id);
