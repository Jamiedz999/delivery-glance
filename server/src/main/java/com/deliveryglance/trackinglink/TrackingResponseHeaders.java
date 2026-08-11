package com.deliveryglance.trackinglink;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;

/**
 * The header set every tracking response carries, in one place so no route can quietly ship without
 * it.
 *
 * <p>{@code no-store} rather than {@code no-cache}: RFC 9111 is explicit that {@code no-cache} still
 * permits storage and only forces revalidation, which is not what a page holding somebody's delivery
 * address needs. {@code no-referrer} rather than the browser default, which would still send a full
 * same-origin path. {@code X-Robots-Tag} is best-effort — a crawler has to choose to obey it, and it
 * is not what keeps the page private; the capability is.
 */
final class TrackingResponseHeaders {

	/**
	 * The policy for the JSON routes. They render nothing, so nothing is allowed: if one is ever
	 * navigated to directly and something decides to treat it as a document, that document can load
	 * no script, no style and no image. {@code nosniff} already makes that unlikely; this makes it
	 * inert as well. The bootstrap page needs a wider policy and supplies its own.
	 */
	private static final String INERT_POLICY = "default-src 'none'; base-uri 'none'; "
			+ "form-action 'none'; frame-ancestors 'none'";

	private TrackingResponseHeaders() {
	}

	static void apply(HttpServletResponse response) {
		apply(response, INERT_POLICY);
	}

	static void apply(HttpServletResponse response, String contentSecurityPolicy) {
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		response.setHeader("Referrer-Policy", "no-referrer");
		response.setHeader("X-Robots-Tag", "noindex, nofollow, nosnippet");
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("Content-Security-Policy", contentSecurityPolicy);
	}

}
