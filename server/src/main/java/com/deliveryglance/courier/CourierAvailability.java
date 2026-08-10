package com.deliveryglance.courier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The narrow, coordinate-free Courier facts the dispatch module needs for eligibility. */
public interface CourierAvailability {

	List<Courier> allCouriers();

	/** Locks the durable account and duty rows while an Assignment is decided. */
	Optional<Courier> lockCourier(UUID courierId);

	record Courier(UUID courierId, String displayName, boolean onDuty) {
	}

}
