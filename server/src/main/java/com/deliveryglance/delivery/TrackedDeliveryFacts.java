package com.deliveryglance.delivery;

import java.util.Optional;
import java.util.UUID;

import com.deliveryglance.trackinglink.TrackedDeliveries;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers trackinglink's two questions about a Delivery. It is a separate bean from {@link
 * Deliveries} on purpose: {@code Deliveries} depends on trackinglink to create a link, so the bean
 * trackinglink depends on has to be one that reads the repository and nothing else, or Spring is
 * asked to build a constructor cycle.
 */
@Component
class TrackedDeliveryFacts implements TrackedDeliveries {

	private final DeliveryRepository repository;

	TrackedDeliveryFacts(DeliveryRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<TrackedDelivery> find(UUID deliveryId) {
		return this.repository.findTrackedDelivery(deliveryId);
	}

}
