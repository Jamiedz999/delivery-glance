package com.deliveryglance.location;

import java.util.UUID;

import jakarta.validation.Valid;

import com.deliveryglance.identityaccess.CurrentActorProvider;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Courier's own Location Sharing controls. Authorization lives in the security policy, and the
 * Courier is always the signed-in one: there is no path here that lets a caller name someone else.
 */
@RestController
@RequestMapping("/api/couriers/me")
class LocationSharingController {

	private final LocationSharing locationSharing;

	private final CurrentActorProvider currentActorProvider;

	LocationSharingController(LocationSharing locationSharing, CurrentActorProvider currentActorProvider) {
		this.locationSharing = locationSharing;
		this.currentActorProvider = currentActorProvider;
	}

	@PostMapping("/location-sharing")
	ResponseEntity<LocationViews.StartedSession> start() {
		LocationViews.StartedSession started = this.locationSharing.start(courierAccountId());
		// The body carries the one copy of the reporting secret, so no cache may keep it.
		return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(started);
	}

	@DeleteMapping("/location-sharing")
	ResponseEntity<Void> stop() {
		this.locationSharing.stop(courierAccountId());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/location-reports")
	LocationViews.Report report(@Valid @RequestBody LocationRequests.Report request) {
		return this.locationSharing.report(courierAccountId(), request);
	}

	private UUID courierAccountId() {
		return this.currentActorProvider.requireCurrentActor().accountId();
	}

}
