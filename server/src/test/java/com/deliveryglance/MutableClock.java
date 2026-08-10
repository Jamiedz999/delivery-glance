package com.deliveryglance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock a test moves by hand. Location rules are all about elapsed time, so the alternative
 * would be sleeping for real seconds and still not being able to reach the two-minute boundary.
 */
public final class MutableClock extends Clock {

	private final ZoneId zone;

	private volatile Instant instant;

	public MutableClock(Instant instant) {
		this(instant, ZoneOffset.UTC);
	}

	private MutableClock(Instant instant, ZoneId zone) {
		this.instant = instant;
		this.zone = zone;
	}

	public void advance(Duration amount) {
		this.instant = this.instant.plus(amount);
	}

	public void set(Instant instant) {
		this.instant = instant;
	}

	@Override
	public Instant instant() {
		return this.instant;
	}

	@Override
	public ZoneId getZone() {
		return this.zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(this.instant, zone);
	}

}
