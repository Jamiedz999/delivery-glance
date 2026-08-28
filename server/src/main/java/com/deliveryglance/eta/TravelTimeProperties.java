package com.deliveryglance.eta;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment inputs for the travel-time provider. The whole feature is gated on {@code providerBaseUrl}:
 * a deployment that sets none has no provider bean — {@link EtaConfig} registers it
 * {@code @ConditionalOnProperty} on this key — so it computes no windows and the Recipient view simply
 * never shows an ETA, exactly as an unset notification queue leaves the outbox unsent.
 *
 * <p>The provider contract itself — terms, DPA and privacy disclosure — and the billing account are
 * arranged where the provider is, not here. What this record carries is only what the running
 * application needs to reach it and to stay inside the agreed spend: the endpoint, the access token,
 * a per-request timeout so a slow provider degrades instead of hanging, and a daily request cap that
 * makes the billing ceiling something the code enforces rather than a note in a runbook.
 *
 * @param providerBaseUrl the provider's base URL — {@code https://api.mapbox.com} in production, a
 * local stub in contract tests. Blank disables the feature entirely.
 * @param accessToken the provider access token. Sent as the provider requires; never logged.
 * @param requestTimeout how long one provider call may take before it is abandoned as unavailable.
 * @param dailyRequestCap the most provider requests made in a day before calls are skipped and
 * windows are left to age out. Zero means uncapped — acceptable in tests and demos, never the
 * production posture ADR 05's billing cap asks for.
 */
@ConfigurationProperties("delivery-glance.eta")
record TravelTimeProperties(String providerBaseUrl, String accessToken, Duration requestTimeout, int dailyRequestCap) {

	TravelTimeProperties {
		requestTimeout = (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative())
				? Duration.ofSeconds(5) : requestTimeout;
		dailyRequestCap = Math.max(dailyRequestCap, 0);
	}

}
