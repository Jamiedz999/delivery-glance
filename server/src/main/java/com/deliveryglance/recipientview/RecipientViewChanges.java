package com.deliveryglance.recipientview;

import java.util.UUID;

import com.deliveryglance.delivery.DeliveryState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Turns "something changed over there" into "the pages watching this Delivery should refetch".
 *
 * <p>Two rules live here and nowhere else.
 *
 * <p>The first is <em>after the change, or not at all</em>. A caller reports a change from inside
 * the transaction that made it, which is the only place it can honestly do so, and this class holds
 * the notification until that transaction commits. A rolled-back Cancel, a version conflict, a
 * duplicate command that threw — none of them reach a stream, and no caller has to remember that,
 * because there is no way to ask for a hint that ignores the outcome.
 *
 * <p>The second is <em>only a change the Recipient can see</em>. A Courier's position moves
 * constantly while they drive to a pickup, and ADR 05 is explicit that a Courier heading to a pickup
 * is not information about this Delivery's journey. Hinting on those would not leak a coordinate —
 * a hint carries none, and the snapshot would come back unchanged — but the timing of the hints
 * would itself describe a Courier on the move, which is the same fact by a slower route. So a
 * location change is only visible In Transit, and that is the only state it produces a hint in.
 */
@Service
class RecipientViewChanges implements RecipientViewUpdates {

	private static final Logger logger = LoggerFactory.getLogger(RecipientViewChanges.class);

	private final RecipientStreams streams;

	private final CarriedDeliveries carriedDeliveries;

	RecipientViewChanges(RecipientStreams streams, CarriedDeliveries carriedDeliveries) {
		this.streams = streams;
		this.carriedDeliveries = carriedDeliveries;
	}

	@Override
	public void deliveryChanged(UUID deliveryId) {
		afterCommit(() -> this.streams.hintChanged(deliveryId));
	}

	@Override
	public void courierPositionChanged(UUID courierAccountId) {
		afterCommit(() -> this.carriedDeliveries.carriedBy(courierAccountId)
			.filter((carried) -> carried.state() == DeliveryState.IN_TRANSIT)
			.ifPresent((carried) -> this.streams.hintChanged(carried.deliveryId())));
	}

	/**
	 * Defers until the caller's transaction commits, or runs now if there is no transaction to wait
	 * for. The second case is not a fallback nobody reaches: Current Location lives in process
	 * memory, so an accepted report is already durable in the only sense it can be by the time it is
	 * reported here.
	 */
	private void afterCommit(Runnable notify) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			run(notify);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				run(notify);
			}
		});
	}

	/**
	 * Nothing a stream does may fail the command that caused it. An exception thrown from an
	 * after-commit callback propagates to whoever called commit, which would turn a delivered
	 * Delivery into a 500 for the Courier who delivered it — over a notification that is, by
	 * design, allowed to be lost.
	 */
	private static void run(Runnable notify) {
		try {
			notify.run();
		}
		catch (RuntimeException ex) {
			logger.warn("Could not notify Recipient streams of a change; connected pages keep the "
					+ "snapshot they have until their next hint or reconnect", ex);
		}
	}

}
