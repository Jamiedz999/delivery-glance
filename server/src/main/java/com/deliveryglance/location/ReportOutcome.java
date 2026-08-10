package com.deliveryglance.location;

/**
 * What a well-formed report did to Current Location. A rejection is an ordinary answer rather than
 * an error: a duplicate, a late arrival or a poor fix is exactly what a moving browser produces,
 * and the Courier is told honestly that the position did not move.
 */
enum ReportOutcome {

	ACCEPTED,

	/** Poorer than one hundred metres, so it cannot stand in for the Courier's position. */
	REJECTED_LOW_ACCURACY,

	/** Measured too far ahead of the server for the device clock to be believed. */
	REJECTED_FUTURE_DATED,

	/** Already past the two-minute boundary when it arrived, so it never becomes usable. */
	REJECTED_STALE,

	/** A duplicate or an out-of-order reading; the stored snapshot already says more. */
	REJECTED_NOT_NEWER

}
