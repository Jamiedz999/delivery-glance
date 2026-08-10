package com.deliveryglance.location;

import java.time.Instant;
import java.util.UUID;

/**
 * What the reporting browser is allowed to see. No coordinate leaves this module: the browser
 * already knows where it is, and nobody else in Core is entitled to the number.
 */
final class LocationViews {

	private LocationViews() {
	}

	/**
	 * The only response that ever carries the reporting secret. It is not readable again, so a page
	 * that loses it has to start a new Location Sharing Session.
	 */
	record StartedSession(UUID generation, String reportingSecret, Instant startedAt) {
	}

	/** What the report did, plus the resulting position facts so the page can re-render from one call. */
	record Report(ReportOutcome outcome, LocationStatus location) {
	}

}
