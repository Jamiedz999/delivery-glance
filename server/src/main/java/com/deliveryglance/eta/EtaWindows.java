package com.deliveryglance.eta;

import java.time.Duration;
import java.time.Instant;

/**
 * The ETA Window arithmetic, kept pure so the honest-uncertainty rules of ADR 05 can be read and
 * tested without a provider, a clock or a database. It turns a travel estimate into a point in time,
 * widens it to the phase's window, and rounds both endpoints outward to five-minute boundaries; and
 * it answers whether a freshly computed window has moved far enough to be worth republishing.
 */
final class EtaWindows {

	private static final long FIVE_MINUTES_SECONDS = Duration.ofMinutes(5).getSeconds();

	private EtaWindows() {
	}

	/**
	 * Draws the window for {@code phase} from a travel estimate. The point estimate is
	 * {@code now + travel + phase buffer}; the window is that point plus or minus the phase's
	 * half-width, with the start floored and the end ceiled to the enclosing five-minute boundary so
	 * the true estimate is always contained.
	 */
	static EtaWindow from(Instant now, Duration travel, EtaPhase phase) {
		Instant point = now.plus(travel).plus(phase.buffer());
		Instant start = floorToFiveMinutes(point.minus(phase.halfWidth()));
		Instant end = ceilToFiveMinutes(point.plus(phase.halfWidth()));
		return new EtaWindow(start, end);
	}

	/**
	 * Whether {@code next} differs enough from the {@code published} window to move it. ADR 05 holds a
	 * window still until an endpoint changes by a full five minutes, which is what stops a window from
	 * twitching every minute on ordinary travel-time noise. A recalculation below the threshold still
	 * counts as successful — only its endpoints are suppressed, never its freshness.
	 */
	static boolean moved(EtaWindow published, EtaWindow next) {
		return shiftedAtLeastFiveMinutes(published.start(), next.start())
				|| shiftedAtLeastFiveMinutes(published.end(), next.end());
	}

	private static boolean shiftedAtLeastFiveMinutes(Instant from, Instant to) {
		return Math.abs(Duration.between(from, to).getSeconds()) >= FIVE_MINUTES_SECONDS;
	}

	private static Instant floorToFiveMinutes(Instant instant) {
		return Instant.ofEpochSecond(Math.floorDiv(instant.getEpochSecond(), FIVE_MINUTES_SECONDS) * FIVE_MINUTES_SECONDS);
	}

	private static Instant ceilToFiveMinutes(Instant instant) {
		return Instant
			.ofEpochSecond(-Math.floorDiv(-instant.getEpochSecond(), FIVE_MINUTES_SECONDS) * FIVE_MINUTES_SECONDS);
	}

}
