package com.deliveryglance.proof;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A Dispatcher's view of one Delivery's proof. It sits under the Delivery namespace the security
 * policy already restricts to a Dispatcher session, so the audience is settled before the request
 * arrives: this is the read surface the privacy decision reserves for the Delivery Team.
 */
@RestController
@RequestMapping("/api/deliveries/{deliveryId}/proof")
class ProofViewController {

	private final ProofViewing viewing;

	ProofViewController(ProofViewing viewing) {
		this.viewing = viewing;
	}

	@GetMapping
	ResponseEntity<ProofViews.ProofSet> forDelivery(@PathVariable UUID deliveryId) {
		return ResponseEntity.ok(this.viewing.forDelivery(deliveryId));
	}

}
