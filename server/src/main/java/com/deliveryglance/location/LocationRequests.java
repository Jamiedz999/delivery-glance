package com.deliveryglance.location;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One position report from the Courier's browser. A structurally impossible point is refused here
 * with a field-level message; whether a structurally valid one is usable is a separate decision made
 * against the stored snapshot.
 *
 * <p>Messages are spelled out rather than left to Bean Validation's translated defaults, so the API
 * contract does not vary with the caller's locale.
 */
final class LocationRequests {

	private static final String REQUIRED = "is required";

	private LocationRequests() {
	}

	record Report(
			@NotNull(message = REQUIRED) UUID generation,

			@NotBlank(message = REQUIRED) @Size(max = 64, message = "must be 64 characters or fewer")
			String reportingSecret,

			@NotNull(message = REQUIRED) @DecimalMin(value = "-180.0", message = "must be between -180 and 180")
			@DecimalMax(value = "180.0", message = "must be between -180 and 180") Double longitude,

			@NotNull(message = REQUIRED) @DecimalMin(value = "-90.0", message = "must be between -90 and 90")
			@DecimalMax(value = "90.0", message = "must be between -90 and 90") Double latitude,

			@NotNull(message = REQUIRED) @PositiveOrZero(message = "must not be negative") Double accuracyMetres,

			/** The device's own measurement time, which is what orders reports. */
			@NotNull(message = REQUIRED) Instant recordedAt) {
	}

}
