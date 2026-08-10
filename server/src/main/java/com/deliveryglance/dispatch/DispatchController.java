package com.deliveryglance.dispatch;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deliveries/{deliveryId}")
class DispatchController {

	private final Dispatches dispatches;

	DispatchController(Dispatches dispatches) {
		this.dispatches = dispatches;
	}

	@GetMapping("/courier-recommendations")
	CourierRecommender.Recommendation recommend(@PathVariable UUID deliveryId) {
		return this.dispatches.recommend(deliveryId);
	}

	@PostMapping("/assignment")
	ResponseEntity<Void> assign(@PathVariable UUID deliveryId, @Valid @RequestBody DispatchRequests.Assign request) {
		this.dispatches.assign(deliveryId, request);
		return ResponseEntity.noContent().build();
	}

}
