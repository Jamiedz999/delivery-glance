package com.deliveryglance.notification;

/**
 * The notification API's responses: the opt-in state a Recipient's page renders, and the dispatch
 * decision the consumer Lambda acts on.
 */
final class NotificationViews {

	private NotificationViews() {
	}

	/**
	 * What the tracking page shows about its own opt-in: the channel and target on file and whether
	 * the subscription is active. Returned to the same grant that created it, so it may echo the
	 * target the Recipient themselves entered.
	 */
	record Subscription(String channel, String target, boolean active) {
	}

	/**
	 * The opt-in section's whole state for a page to render. {@code available} is whether this
	 * deployment can actually deliver a notification — false when no queue is configured — so the
	 * page offers the control only when opting in would do something, never a form that leads
	 * nowhere. {@code subscription} is the Recipient's current opt-in, or null when they have none.
	 */
	record OptInState(boolean available, Subscription subscription) {
	}

	/**
	 * The answer to a consumer's begin call. {@code status} is the whole instruction:
	 * <ul>
	 * <li>{@code PROCEED} — send now through {@code channel} to {@code target}, with the state and
	 * Reference for the message; the send must then be confirmed.</li>
	 * <li>{@code ALREADY_SENT} — a send is recorded; do nothing.</li>
	 * <li>{@code SUPPRESSED} — the Recipient unsubscribed before this was sent; do nothing.</li>
	 * <li>{@code UNKNOWN} — no outbox row for this transition; do nothing.</li>
	 * </ul>
	 * Only {@code PROCEED} carries a channel, target, state and Reference; the other three carry
	 * nothing, so a decision not to send never puts a volunteered address on the wire.
	 */
	record DispatchDecision(String status, String channel, String target, String nextState,
			String deliveryReference) {

		static DispatchDecision proceed(NotificationChannel channel, String target, String nextState,
				String deliveryReference) {
			return new DispatchDecision("PROCEED", channel.name(), target, nextState, deliveryReference);
		}

		static DispatchDecision alreadySent() {
			return new DispatchDecision("ALREADY_SENT", null, null, null, null);
		}

		static DispatchDecision suppressed() {
			return new DispatchDecision("SUPPRESSED", null, null, null, null);
		}

		static DispatchDecision unknown() {
			return new DispatchDecision("UNKNOWN", null, null, null, null);
		}

	}

}
