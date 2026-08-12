package com.deliveryglance.demo;

import java.util.List;

import com.deliveryglance.delivery.DeliveryProvisioning;
import com.deliveryglance.location.SharedPositionReset;

import org.springframework.transaction.annotation.Transactional;

/**
 * Puts the demo back to the state its walkthrough starts from: two fictional Deliveries Awaiting a
 * Courier, a Courier who is Off Duty and sharing nothing, and no Tracking Link anyone was given
 * earlier still working.
 *
 * <p>It is one transaction, so a reset that fails leaves the demo it found rather than half of one.
 */
class DemoReset {

	private final DemoResetRepository repository;

	private final DeliveryProvisioning deliveries;

	private final SharedPositionReset positions;

	DemoReset(DemoResetRepository repository, DeliveryProvisioning deliveries, SharedPositionReset positions) {
		this.repository = repository;
		this.deliveries = deliveries;
		this.positions = positions;
	}

	/**
	 * @return the References it created, in the order it created them, so a caller driving the demo
	 * from a script knows what to ask for next without guessing.
	 */
	@Transactional
	List<String> reset() {
		this.repository.deleteEveryDeliveryAndCourierFact();
		// In memory, so it is not part of the transaction and a rollback does not put the coordinates
		// back. That is the right way round: a forgotten position is the same Unavailable a restart
		// produces, and the next report fixes it, whereas a position surviving a reset would be a
		// Courier still on a map for a Delivery that no longer exists.
		this.positions.forgetEveryPosition();

		DemoDelivery.CATALOGUE.forEach(this.deliveries::createAwaitingCourier);
		return DemoDelivery.CATALOGUE.stream().map(DeliveryProvisioning.NewDelivery::reference).toList();
	}

}
