package com.deliveryglance.trackinglink;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment inputs for Tracking Links.
 *
 * <p>The keys are a map rather than one value so a key can be replaced without invalidating links
 * issued under the previous one: new links use {@code currentKeyVersion}, and existing links keep
 * rederiving under the version recorded against them until they expire.
 *
 * @param keys HMAC key material by version, as UTF-8 text
 * @param currentKeyVersion the version new links are issued under; must be present in {@code keys}
 * @param cookieSecure whether the grant cookie is marked {@code Secure}. Production defaults to
 * true; the plain-HTTP local Compose demo is the exception and has to say so explicitly.
 * @param mapStyleUrl the map style the tracking page loads, or blank when none is configured. It
 * belongs to the page rather than to the Recipient projection because the page is what has to name
 * the tile host in its Content-Security-Policy, and a policy assembled from a second copy of this
 * value would be a policy that could disagree with the URL it is meant to allow. Blank is a
 * supported deployment: the view keeps its status and freshness content and says the map is
 * unavailable.
 */
@ConfigurationProperties("delivery-glance.tracking")
record TrackingLinkProperties(Map<Integer, String> keys, int currentKeyVersion, boolean cookieSecure,
		String mapStyleUrl) {
}
