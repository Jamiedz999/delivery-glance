package com.deliveryglance.delivery;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Dispatcher-facing Delivery API. Authorization lives in the security policy, so every method
 * here can assume a signed-in Dispatcher.
 */
@RestController
@RequestMapping("/api/deliveries")
class DeliveryController {

	private final Deliveries deliveries;

	DeliveryController(Deliveries deliveries) {
		this.deliveries = deliveries;
	}

	@PostMapping
	ResponseEntity<DeliveryViews.Detail> create(@Valid @RequestBody DeliveryRequests.Create request) {
		DeliveryViews.Detail created = this.deliveries.create(request);
		return ResponseEntity.created(URI.create("/api/deliveries/" + created.id())).body(created);
	}

	@GetMapping
	List<DeliveryViews.Summary> list() {
		return this.deliveries.list();
	}

	@GetMapping("/{id}")
	DeliveryViews.Detail detail(@PathVariable UUID id) {
		return this.deliveries.detail(id);
	}

	@PostMapping("/{id}/cancel")
	DeliveryViews.Detail cancel(@PathVariable UUID id, @Valid @RequestBody DeliveryRequests.Cancel request) {
		return this.deliveries.cancel(id, request);
	}

}
