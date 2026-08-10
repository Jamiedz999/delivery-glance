package com.deliveryglance.delivery;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in Courier's current Delivery and explicit pickup/handoff commands. */
@RestController
@RequestMapping("/api/couriers/me/deliveries")
class CourierDeliveryController {

	private final Deliveries deliveries;

	CourierDeliveryController(Deliveries deliveries) {
		this.deliveries = deliveries;
	}

	@GetMapping("/current")
	ResponseEntity<DeliveryViews.CourierDelivery> current() {
		return this.deliveries.currentForCourier()
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@PostMapping("/{deliveryId}/pickup")
	ResponseEntity<Void> confirmPickup(@PathVariable UUID deliveryId,
			@Valid @RequestBody DeliveryRequests.Progress request) {
		this.deliveries.confirmPickup(deliveryId, request);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{deliveryId}/handoff")
	ResponseEntity<Void> confirmHandoff(@PathVariable UUID deliveryId,
			@Valid @RequestBody DeliveryRequests.Progress request) {
		this.deliveries.confirmHandoff(deliveryId, request);
		return ResponseEntity.noContent().build();
	}

}
