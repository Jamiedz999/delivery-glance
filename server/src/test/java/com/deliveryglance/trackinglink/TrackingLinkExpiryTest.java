package com.deliveryglance.trackinglink;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two limits ADR 06 fixes, and the rule that whichever comes first wins. Opening, Copy and reuse
 * appear nowhere in this class, which is the point: nothing a holder does can move either limit.
 */
class TrackingLinkExpiryTest {

	private static final Instant ISSUED = Instant.parse("2026-08-10T09:00:00Z");

	private static final Instant SEVEN_DAYS_LATER = ISSUED.plus(Duration.ofDays(7));

	@Test
	void expiresSevenDaysAfterIssueWhileTheDeliveryIsStillRunning() {
		assertThat(TrackingLinkExpiry.effective(SEVEN_DAYS_LATER, null)).isEqualTo(SEVEN_DAYS_LATER);
	}

	@Test
	void expiresTwentyFourHoursAfterATerminalTransitionThatArrivesFirst() {
		Instant delivered = ISSUED.plus(Duration.ofHours(3));

		assertThat(TrackingLinkExpiry.effective(SEVEN_DAYS_LATER, delivered))
			.isEqualTo(delivered.plus(Duration.ofHours(24)));
	}

	/**
	 * A Delivery that reaches a terminal state on its last day gets the grace period it has left,
	 * not a fresh twenty-four hours: the seven-day cap is absolute.
	 */
	@Test
	void keepsTheSevenDayCapWhenTheTerminalGracePeriodWouldOutrunIt() {
		Instant deliveredNearTheEnd = SEVEN_DAYS_LATER.minus(Duration.ofHours(2));

		assertThat(TrackingLinkExpiry.effective(SEVEN_DAYS_LATER, deliveredNearTheEnd)).isEqualTo(SEVEN_DAYS_LATER);
	}

	@Test
	void treatsATerminalTransitionExactlyOneDayBeforeTheCapAsTheSameMoment() {
		Instant deliveredExactlyOneDayEarly = SEVEN_DAYS_LATER.minus(Duration.ofHours(24));

		assertThat(TrackingLinkExpiry.effective(SEVEN_DAYS_LATER, deliveredExactlyOneDayEarly))
			.isEqualTo(SEVEN_DAYS_LATER);
	}

	/**
	 * Half-open, and it has to be. A grant is written with the link's expiry as its own, and
	 * {@code tracking_grant} requires that to be strictly after the moment it was established — so
	 * counting the expiry instant as valid would mean answering a link exchanged on the tick with a
	 * constraint violation rather than with the Unavailable response.
	 */
	@Test
	void stopsCountingTheLinkValidAtItsExpiryInstant() {
		assertThat(TrackingLinkExpiry.isValidAt(SEVEN_DAYS_LATER, SEVEN_DAYS_LATER.minusMillis(1))).isTrue();
		assertThat(TrackingLinkExpiry.isValidAt(SEVEN_DAYS_LATER, SEVEN_DAYS_LATER)).isFalse();
	}

}
