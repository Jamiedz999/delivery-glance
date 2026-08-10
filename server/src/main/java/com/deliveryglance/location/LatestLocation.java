package com.deliveryglance.location;

import java.time.Instant;
import java.util.UUID;

/**
 * A Courier's Current Location as one immutable, complete snapshot. A newer usable reading replaces
 * the whole record; no field is ever updated in place, so a snapshot cannot end up describing two
 * different moments.
 *
 * @param receivedAt when the server accepted it; useful for diagnostics, never for freshness
 */
record LatestLocation(UUID generation, double longitude, double latitude, double accuracyMetres, Instant recordedAt,
		Instant receivedAt) {
}
