package com.deliveryglance.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SystemController {

	private final String applicationName;

	private final boolean proofCaptureEnabled;

	private final boolean etaEnabled;

	SystemController(@Value("${spring.application.name}") String applicationName,
			// Read straight from configuration rather than through the proof module, so the public
			// probe stays a leaf that depends on nothing: a blank bucket is a deployment without
			// proof, and the value is a fact about configuration, not a policy.
			@Value("${delivery-glance.proof.bucket:}") String proofBucket,
			// Same rule for ETA: a blank provider base URL is a deployment without travel-time
			// windows, read as configuration rather than through the eta module.
			@Value("${delivery-glance.eta.provider-base-url:}") String etaProviderBaseUrl) {
		this.applicationName = applicationName;
		this.proofCaptureEnabled = !proofBucket.isBlank();
		this.etaEnabled = !etaProviderBaseUrl.isBlank();
	}

	@GetMapping("/api/system")
	SystemStatus systemStatus() {
		return new SystemStatus(this.applicationName, "ok", this.proofCaptureEnabled, this.etaEnabled);
	}

}
