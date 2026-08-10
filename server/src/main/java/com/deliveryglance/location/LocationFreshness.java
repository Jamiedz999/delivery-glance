package com.deliveryglance.location;

import java.time.Duration;
import java.time.Instant;

/**
 * How recently the stored position was measured, in the three words every role sees. It is derived
 * from the device-recorded measurement time, never from when the report arrived: a slow upload
 * cannot make an old position look current.
 */
public enum LocationFreshness {

	/** Measured within the last thirty seconds, so it may be presented as the current position. */
	LIVE,

	/** Older than that but still usable, so it is shown with its age and never as live. */
	DELAYED,

	/** No position exists, or the last one is past the point where its coordinates are kept. */
	UNAVAILABLE;

	static final Duration LIVE_LIMIT = Duration.ofSeconds(30);

	/** Past this age the coordinates are deleted rather than shown with a bigger number. */
	static final Duration USABLE_LIMIT = Duration.ofMinutes(2);

	static LocationFreshness of(Instant recordedAt, Instant now) {
		Duration age = Duration.between(recordedAt, now);
		if (age.compareTo(LIVE_LIMIT) <= 0) {
			return LIVE;
		}
		return (age.compareTo(USABLE_LIMIT) <= 0) ? DELAYED : UNAVAILABLE;
	}

}
