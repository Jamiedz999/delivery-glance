package com.deliveryglance.trackinglink;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.deliveryglance.shared.Secrets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

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
 * a personalised preview would hand them the address. The one configured value it does carry, the
 * map style, is the same for every visitor and says nothing about anybody's delivery.
 */
@Component
class TrackingBootstrapPage {

	/**
	 * {@code default-src 'none'} means the page can load nothing at all unless a directive below
	 * allows it. The inlined bootstrap is pinned by hash; {@code 'self'} additionally admits the
	 * Recipient application, which is a first-party build artefact the bootstrap requests by a fixed
	 * path only after the exchange has succeeded. {@code connect-src 'self'} is what lets the
	 * exchange and the snapshot happen and nothing else leave. {@code worker-src 'self' blob:} is
	 * MapLibre, which parses vector tiles off the main thread: {@code 'self'} for the worker bundle
	 * the build emits beside the application, and {@code blob:} for the fallback it constructs
	 * itself when a module worker is refused.
	 *
	 * <p>The tile host is appended to {@code img-src} and {@code connect-src} when one is
	 * configured, and to nothing else. A style URL therefore widens exactly the two directives a map
	 * needs and cannot introduce a script origin.
	 */
	private static final String CSP_TEMPLATE = "default-src 'none'; script-src 'sha256-%s' 'self'; "
			+ "style-src 'sha256-%s' 'self'; img-src 'self' data: blob:%s; connect-src 'self'%s; "
			+ "worker-src 'self' blob:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

	private static final String STYLE = """
			body{font:16px/1.5 system-ui,sans-serif;margin:0;padding:2rem 1.25rem;max-width:34rem}""";

	private final String html;

	private final String contentSecurityPolicy;

	TrackingBootstrapPage(TrackingLinkProperties properties) {
		String script = read("tracking/bootstrap.js");
		String tileOrigin = tileOriginOf(properties.mapStyleUrl());
		String allowed = tileOrigin.isEmpty() ? "" : " " + tileOrigin;
		this.contentSecurityPolicy = CSP_TEMPLATE.formatted(sha256Base64(script), sha256Base64(STYLE), allowed,
				allowed);
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
				<meta name="delivery-glance-map-style" content="%s">
				<style>%s</style>
				</head>
				<body>
				<main>
				<h1>Delivery Glance</h1>
				<p id="tracking-status" role="status">Opening your tracking link…</p>
				<div id="tracking-app"></div>
				</main>
				<script>%s</script>
				</body>
				</html>
				""".formatted(HtmlUtils.htmlEscape(nullToEmpty(properties.mapStyleUrl())), STYLE, script);
	}

	String html() {
		return this.html;
	}

	String contentSecurityPolicy() {
		return this.contentSecurityPolicy;
	}

	/**
	 * The scheme, host and port the configured style is served from, or empty when no style is
	 * configured. Tiles are assumed to come from the style's own origin, which is what every hosted
	 * style provider does; a style that references another host needs that host added here rather
	 * than discovered at runtime, because a policy the page could widen on demand would not be one.
	 *
	 * @throws IllegalStateException if a style is configured but is not an absolute URL. Deriving
	 * nothing from it would leave the map silently unable to load its tiles, and an operational
	 * mistake that presents as "the map is unavailable" is the hardest kind to find.
	 */
	private static String tileOriginOf(String styleUrl) {
		String configured = nullToEmpty(styleUrl).strip();
		if (configured.isEmpty()) {
			return "";
		}
		try {
			URI uri = new URI(configured);
			if (uri.getScheme() == null || uri.getHost() == null) {
				throw new IllegalStateException(
						"The configured tracking map style URL must be absolute, including its scheme and host");
			}
			String port = (uri.getPort() == -1) ? "" : ":" + uri.getPort();
			return uri.getScheme() + "://" + uri.getHost() + port;
		}
		catch (URISyntaxException ex) {
			throw new IllegalStateException("The configured tracking map style URL is not a valid URL", ex);
		}
	}

	private static String nullToEmpty(String value) {
		return (value == null) ? "" : value;
	}

	private static String read(String path) {
		try {
			return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("The tracking bootstrap script is missing from the application", ex);
		}
	}

	/** Standard base64, not the url-safe alphabet: CSP hashes are quoted, not put in a URL. */
	private static String sha256Base64(String source) {
		return Base64.getEncoder().encodeToString(Secrets.digest(source));
	}

}
