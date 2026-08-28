package com.deliveryglance.eta;

import java.util.UUID;

/**
 * How delivery tells eta that a Delivery just changed phase — a Courier was assigned, or a pickup
 * turned it In Transit — so a fresh window can be drawn at once rather than waiting up to a minute
 * for the sweeper. ADR 05 asks for an ETA "immediately at Assignment and pickup"; this is that
 * immediacy, and it is the only way delivery reaches eta.
 *
 * <p>Call it on the success path, after the transition has committed. A recalculation that fails —
 * no usable location, a provider fault — changes nothing a caller must handle: it simply leaves no
 * window, and the transaction that triggered it is already done and cannot be rolled back by an ETA.
 */
public interface EtaRecalculation {

	void deliveryPhaseChanged(UUID deliveryId);

}
