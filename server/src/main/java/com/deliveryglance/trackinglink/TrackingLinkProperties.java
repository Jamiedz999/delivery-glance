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
 */
@ConfigurationProperties("delivery-glance.tracking")
record TrackingLinkProperties(Map<Integer, String> keys, int currentKeyVersion, boolean cookieSecure) {
}
