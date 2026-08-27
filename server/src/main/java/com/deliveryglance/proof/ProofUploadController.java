package com.deliveryglance.proof;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Courier's request for one upload URL. It lives under the Courier's own Delivery namespace and
 * the security policy already restricts {@code /api/couriers/**} to a Courier session; the
 * finer-grained check — that this Courier carries this Delivery — is the service's.
 */
@RestController
@RequestMapping("/api/couriers/me/deliveries/{deliveryId}/proof-uploads")
class ProofUploadController {

	private final ProofUploads uploads;

	ProofUploadController(ProofUploads uploads) {
		this.uploads = uploads;
	}

	@PostMapping
	ResponseEntity<ProofUploads.IssuedUpload> requestUpload(@PathVariable UUID deliveryId,
			@Valid @RequestBody ProofRequests.UploadTicket request) {
		return ResponseEntity.ok(this.uploads.issue(deliveryId, request.kind(), request.contentType()));
	}

}
