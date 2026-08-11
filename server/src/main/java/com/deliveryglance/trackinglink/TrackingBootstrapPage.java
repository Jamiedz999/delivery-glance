package com.deliveryglance.trackinglink;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The /track page: generic first-party HTML with the one bootstrap script inlined, and the CSP that
 * pins it.
 *
 * <p>It is assembled once at startup so the script and the {@code sha256-} hash in its policy cannot
 * drift apart — the hash is computed from the same bytes that get inlined, rather than written down
 * next to them and hoped over.
 *
 * <p>Nothing in the markup varies by Delivery. Preview metadata says "Delivery Glance" and no more,
 * because a link unfurled in a chat app is fetched by that app's servers, not by the Recipient, and
 * a personalised preview would hand them the address.
 */
@Component
class TrackingBootstrapPage {

	/**
	 * {@code default-src 'none'} means the page can load nothing at all unless a directive below
	 * allows it, so a script that arrives by any route other than the inlined one simply does not
	 * run. {@code connect-src 'self'} is what lets the exchange happen and nothing else leave.
	 */
	private static final String CSP_TEMPLATE = "default-src 'none'; script-src 'sha256-%s'; "
			+ "style-src 'sha256-%s'; connect-src 'self'; base-uri 'none'; form-action 'none'; "
			+ "frame-ancestors 'none'";

	private static final String STYLE = """
			body{font:16px/1.5 system-ui,sans-serif;margin:0;padding:2rem 1.25rem;max-width:34rem}""";

	private final String html;

	private final String contentSecurityPolicy;

	TrackingBootstrapPage() {
		String script = read("tracking/bootstrap.js");
		this.contentSecurityPolicy = CSP_TEMPLATE.formatted(sha256Base64(script), sha256Base64(STYLE));
		this.html = """
				<!doctype html>
				<html lang="en">
				<head>
				<meta charset="utf-8">
				<meta name="viewport" content="width=device-width, initial-scale=1">
				<meta name="robots" content="noindex, nofollow, nosnippet">
				<title>Delivery Glance</title>
				<meta property="og:title" content="Delivery Glance">
				<meta property="og:description" content="Delivery Glance tracking link">
				<style>%s</style>
				</head>
				<body>
				<main>
				<h1>Delivery Glance</h1>
				<p id="tracking-status" role="status">Opening your tracking link…</p>
				<p id="tracking-content"></p>
				</main>
				<script>%s</script>
				</body>
				</html>
				""".formatted(STYLE, script);
	}

	String html() {
		return this.html;
	}

	String contentSecurityPolicy() {
		return this.contentSecurityPolicy;
	}

	private static String read(String path) {
		try {
			return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("The tracking bootstrap script is missing from the application", ex);
		}
	}

	private static String sha256Base64(String source) {
		try {
			return Base64.getEncoder()
				.encodeToString(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by every Java platform", ex);
		}
	}

}
