package com.deliveryglance.trackinglink;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The Dispatcher's Tracking Link commands. Only Revocation exists in Core; Rotation and Reissue are
 * later sub-issues of #28.
 *
 * <p>Every message is spelled out rather than left to Bean Validation's locale-translated defaults,
 * so the API contract reads the same whatever the caller's browser asks for.
 */
final class TrackingLinkRequests {

	private static final String REQUIRED = "is required";

	private TrackingLinkRequests() {
	}

	/**
	 * End access through the current link without a replacement. Carries no version or command id:
	 * Revocation is idempotent by its terminal nature — a link is revoked once and a second attempt
	 * is refused because it is already revoked, not because a version moved on.
	 */
	record Revoke(
			@NotNull(message = REQUIRED) TrackingLinkChangeReason reason,

			@Size(max = 500, message = "must be 500 characters or fewer") String note) {

		@AssertTrue(message = "a note is required when the reason is OTHER")
		boolean isNotePresentWhenReasonIsOther() {
			return this.reason != TrackingLinkChangeReason.OTHER || (this.note != null && !this.note.isBlank());
		}

		@AssertTrue(message = "this reason cannot be used to revoke a Tracking Link")
		boolean isReasonApplicableToRevocation() {
			// null is the @NotNull check's business; answering true here keeps one missing reason
			// from drawing two field errors.
			return this.reason == null || this.reason.appliesToRevocation();
		}

	}

}
