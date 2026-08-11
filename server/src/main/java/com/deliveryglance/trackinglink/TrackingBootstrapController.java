package com.deliveryglance.trackinglink;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves /track.
 *
 * <p>This controller does not take a token parameter, does not read one, and has nothing to consume.
 * That is the design, not an omission: RFC 9110 makes GET safe, and link previews, mail scanners and
 * prefetchers act on that — Microsoft 365 Safe Links and Slack unfurling both fetch a URL before any
 * person clicks it. A first open that activated the link would mean the link was routinely spent by
 * software on the way to the Recipient.
 *
 * <p>Spring MVC answers HEAD from this same mapping by running it and discarding the body, so HEAD
 * is side-effect free for the same reason GET is.
 */
@RestController
class TrackingBootstrapController {

	private final TrackingBootstrapPage page;

	TrackingBootstrapController(TrackingBootstrapPage page) {
		this.page = page;
	}

	@GetMapping(path = "/track", produces = MediaType.TEXT_HTML_VALUE)
	@ResponseBody
	String bootstrap(HttpServletResponse response) {
		TrackingResponseHeaders.apply(response);
		response.setHeader("Content-Security-Policy", this.page.contentSecurityPolicy());
		return this.page.html();
	}

	/**
	 * Anything under /track is the same generic page. A path segment is not a second way to present
	 * a capability, and it must not become one by accident when DG-025 adds client-side routes.
	 */
	@GetMapping(path = "/track/**", produces = MediaType.TEXT_HTML_VALUE)
	@ResponseBody
	String bootstrapSubPath(HttpServletResponse response) {
		return bootstrap(response);
	}

}
