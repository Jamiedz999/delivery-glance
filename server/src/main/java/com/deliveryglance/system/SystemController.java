package com.deliveryglance.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SystemController {

	private final String applicationName;

	private final boolean proofCaptureEnabled;

	private final boolean etaEnabled;

	private final DemoAccountsProbe demoAccountsProbe;

	SystemController(@Value("${spring.application.name}") String applicationName,
			// Read straight from configuration rather than through the proof module: a blank bucket is
			// a deployment without proof, and the value is a fact about configuration, not a policy.
			// Every field here but demoAccountsUnchanged (below) is answerable from configuration alone.
			@Value("${delivery-glance.proof.bucket:}") String proofBucket,
			// Same rule for ETA: a blank provider base URL is a deployment without travel-time
			// windows, read as configuration rather than through the eta module.
			@Value("${delivery-glance.eta.provider-base-url:}") String etaProviderBaseUrl,
			// The one field configuration cannot answer: whether the two seeded Internal Accounts are
			// still the fictional demo ones. Any deployment may reseed them, so it is decided from the
			// internal_account rows, read once at startup — see DemoAccountsProbe.
			DemoAccountsProbe demoAccountsProbe) {
		this.applicationName = applicationName;
		this.proofCaptureEnabled = !proofBucket.isBlank();
		this.etaEnabled = !etaProviderBaseUrl.isBlank();
		this.demoAccountsProbe = demoAccountsProbe;
	}

	@GetMapping("/api/system")
	SystemStatus systemStatus() {
		return new SystemStatus(this.applicationName, "ok", this.proofCaptureEnabled, this.etaEnabled,
				this.demoAccountsProbe.demoAccountsUnchanged());
	}

}
