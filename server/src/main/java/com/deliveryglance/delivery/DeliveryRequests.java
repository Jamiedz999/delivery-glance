package com.deliveryglance.delivery;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * The commands a Dispatcher can send. Coordinates are checked against the WGS84 ranges here as well
 * as in the database, so a bad point is answered with a field-level message rather than an error.
 *
 * <p>Every message is spelled out instead of relying on Bean Validation's defaults: those are
 * translated for the caller's locale, which would make the API contract vary by browser and would
 * mix languages inside one form.
 */
final class DeliveryRequests {

	private static final String REQUIRED = "is required";

	private DeliveryRequests() {
	}

	record Create(
			@NotBlank(message = REQUIRED) @Size(max = 64, message = "must be 64 characters or fewer")
			@Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9 /_-]*",
					message = "must start with a letter or digit and use only letters, digits, spaces, '-', '_' or '/'")
			String reference,

			@NotNull(message = REQUIRED) @Valid Address pickup,

			@NotNull(message = REQUIRED) @Valid Address handoff) {
	}

	record Address(
			@NotBlank(message = REQUIRED) @Size(max = 255, message = "must be 255 characters or fewer")
			String addressLabel,

			@NotNull(message = REQUIRED) @DecimalMin(value = "-90.0", message = "must be between -90 and 90")
			@DecimalMax(value = "90.0", message = "must be between -90 and 90") Double latitude,

			@NotNull(message = REQUIRED) @DecimalMin(value = "-180.0", message = "must be between -180 and 180")
			@DecimalMax(value = "180.0", message = "must be between -180 and 180") Double longitude) {
	}

	record Cancel(
			/** Makes a retried cancellation a no-op rather than a second attempt. */
			@NotNull(message = REQUIRED) UUID commandId,

			@NotNull(message = REQUIRED) @PositiveOrZero(message = "must not be negative") Integer expectedVersion,

			@NotNull(message = REQUIRED) CancellationReason reason,

			@Size(max = 500, message = "must be 500 characters or fewer") String note) {

		@AssertTrue(message = "a note is required when the reason is OTHER")
		boolean isNotePresentWhenReasonIsOther() {
			return this.reason != CancellationReason.OTHER || (this.note != null && !this.note.isBlank());
		}

	}

}
