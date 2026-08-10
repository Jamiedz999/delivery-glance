package com.deliveryglance.location;

import java.time.Instant;
import java.util.UUID;

/**
 * One attempted position report, already proven to come from the Courier's current Location
 * Sharing Session. Whether it is usable is still the store's decision.
 *
 * @param recordedAt when the device measured the position, which is what orders reports
 */
record LocationReport(UUID courierAccountId, UUID generation, double longitude, double latitude,
		double accuracyMetres, Instant recordedAt) {
}
