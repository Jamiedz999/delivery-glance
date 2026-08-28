package com.deliveryglance.eta;

import java.util.UUID;

/**
 * How eta tells whoever is watching that a Delivery's published window moved. It exists so eta does
 * not have to import the Recipient view to nudge an open page: recipientview implements this, eta
 * only announces. The seam carries a Delivery id and nothing else — eta says "this window changed",
 * never what a Recipient may see of it, which stays the recipient view's decision.
 *
 * <p>It fires only on a real move — a first window, an endpoint shifting a full five minutes, or a
 * window being withdrawn — not on every successful recalculation, because a window that did not
 * budge gives an open page nothing new to fetch.
 */
public interface EtaChanges {

	void etaChanged(UUID deliveryId);

}
