package com.deliveryglance.recipientview;

import java.util.UUID;

/**
 * How a module that has just changed something tells the Recipient view that an open page is now
 * looking at an old answer.
 *
 * <p>Both methods are told what changed, never what to send. That is the whole point of the seam:
 * delivery knows a transition committed and location knows a reading was accepted, but neither
 * knows which of those facts a Recipient is allowed to see, and a caller that could choose the
 * payload would be deciding that. What actually goes down the wire is settled in this package, and
 * it is never more than "refetch".
 *
 * <p>Call these on the ordinary success path, inside the transaction that made the change. The
 * implementation defers the notification until that transaction commits, so a command that rolls
 * back emits nothing and no caller has to remember the distinction.
 */
public interface RecipientViewUpdates {

	/** A Delivery's own durable state changed — a transition committed. */
	void deliveryChanged(UUID deliveryId);

	/**
	 * A Courier's Current Location changed — replaced by an accepted report, or withdrawn by Stop,
	 * by signing out, or by a new Location Sharing Session starting.
	 *
	 * <p>It names the Courier because that is all location knows; which Delivery, if any, that is
	 * currently visible on is worked out here.
	 *
	 * <p>Two things are deliberately not changes. A rejected report left Current Location exactly as
	 * it was. And a reading ageing past the usable limit is something the page works out for itself
	 * on the same rule at the same moment, so reporting it would be a hint nobody needed.
	 */
	void courierPositionChanged(UUID courierAccountId);

}
