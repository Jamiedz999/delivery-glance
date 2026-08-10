package com.deliveryglance.courier;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Courier's own record. There is no route to another Courier's: everything here is scoped to
 * the signed-in account by the security policy rather than by a path variable.
 */
@RestController
@RequestMapping("/api/couriers/me")
class CourierController {

	private final Couriers couriers;

	CourierController(Couriers couriers) {
		this.couriers = couriers;
	}

	@GetMapping
	CourierViews.Courier me() {
		return this.couriers.me();
	}

	@PutMapping("/duty")
	CourierViews.Courier updateDuty(@Valid @RequestBody CourierRequests.Duty request) {
		return this.couriers.updateDuty(request);
	}

}
