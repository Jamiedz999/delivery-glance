package com.deliveryglance.proof;

import jakarta.validation.Valid;

import com.deliveryglance.shared.CallbackToken;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The processing Lambda's way back into the application. It is the one route not reached by a
 * browser or an Internal Account: the security policy lets it through unauthenticated, and this
 * controller is where it is actually authorized, by a shared bearer token compared in constant
 * time. A deployment with no token configured has no Lambda, and every callback is refused.
 */
@RestController
@RequestMapping("/api/internal/proof-processed")
class ProofProcessingController {

	private final ProofProcessing processing;

	private final ProofProperties properties;

	ProofProcessingController(ProofProcessing processing, ProofProperties properties) {
		this.processing = processing;
		this.properties = properties;
	}

	@PostMapping
	ResponseEntity<Void> processed(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Valid @RequestBody ProofRequests.ProcessingCallback callback) {
		requireCallbackToken(authorization);
		this.processing.settle(callback);
		return ResponseEntity.noContent().build();
	}

	private void requireCallbackToken(String authorizationHeader) {
		if (!CallbackToken.authorizes(authorizationHeader, this.properties.callbackToken())) {
			throw ProofException.callbackUnauthorized();
		}
	}

}
