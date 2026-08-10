package com.deliveryglance.courier;

import java.time.Instant;

import com.deliveryglance.location.LocationStatus;

/**
 * What the Courier's own workspace is allowed to see about them. It deliberately carries no
 * coordinate and no reusable reporting secret: a page that reloads must start Location Sharing
 * again rather than pick up where the previous page left off.
 */
final class CourierViews {

	private CourierViews() {
	}

	/**
	 * @param onDutyChangedAt null until the Courier has ever declared a duty state
	 * @param sharing null when no Location Sharing Session is running
	 */
	record Courier(String displayName, boolean onDuty, Instant onDutyChangedAt, Sharing sharing,
			LocationStatus location) {
	}

	record Sharing(Instant startedAt) {
	}

}
