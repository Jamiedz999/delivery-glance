package com.deliveryglance.trackinglink;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * The /track page's policy, which is the one place a configured map style can widen what the
 * Recipient's browser is allowed to do. Every assertion here is about the size of that hole.
 */
class TrackingBootstrapPageTest {

	private static final String STYLE_URL = "https://tiles.delivery-glance.example/styles/core/style.json?key=demo";

	private static final String TILE_ORIGIN = "https://tiles.delivery-glance.example";

	@Test
	void admitsTheFirstPartyApplicationWithoutAdmittingInlineScript() {
		String policy = pageWith("").contentSecurityPolicy();

		assertThat(policy).containsPattern("script-src 'sha256-[A-Za-z0-9+/=]+' 'self'")
			.contains("default-src 'none'")
			.contains("frame-ancestors 'none'")
			.doesNotContain("'unsafe-inline'")
			.doesNotContain("'unsafe-eval'");
	}

	/**
	 * A style URL is deployment configuration, and configuration must not be able to introduce a
	 * script origin. It widens where tiles may be fetched from and nothing else.
	 */
	@Test
	void letsAConfiguredStyleWidenOnlyWhereTilesComeFrom() {
		String policy = pageWith(STYLE_URL).contentSecurityPolicy();

		assertThat(policy).contains("img-src 'self' data: blob: " + TILE_ORIGIN)
			.contains("connect-src 'self' " + TILE_ORIGIN);
		assertThat(policy.substring(policy.indexOf("script-src"), policy.indexOf("style-src")))
			.doesNotContain(TILE_ORIGIN);
	}

	@Test
	void allowsNoTileHostAtAllWhenNoStyleIsConfigured() {
		String policy = pageWith("").contentSecurityPolicy();

		assertThat(policy).contains("img-src 'self' data: blob:;").contains("connect-src 'self';");
	}

	@Test
	void keepsThePortWhenAStyleIsServedFromOne() {
		assertThat(pageWith("http://localhost:8081/style.json").contentSecurityPolicy())
			.contains("connect-src 'self' http://localhost:8081;");
	}

	/**
	 * Deriving nothing from a misconfigured URL would leave the map unable to load its tiles, and an
	 * operational mistake that presents to the Recipient as "the map is unavailable" is the hardest
	 * kind to find. It fails at startup instead.
	 */
	@Test
	void refusesToStartWhenAConfiguredStyleUrlIsNotAbsolute() {
		assertThatIllegalStateException().isThrownBy(() -> pageWith("/styles/core/style.json"));
		assertThatIllegalStateException().isThrownBy(() -> pageWith("not a url at all"));
	}

	@Test
	void handsTheStyleToTheApplicationThroughTheMarkupRatherThanTheSnapshot() {
		String html = pageWith(STYLE_URL).html();

		assertThat(html).contains("<meta name=\"delivery-glance-map-style\" content=\"" + STYLE_URL + "\">")
			.contains("<div id=\"tracking-app\"></div>");
	}

	/**
	 * The value is configuration rather than user input, and it still reaches an HTML attribute and
	 * still gets escaped. A query string with two parameters is the ordinary case, not a contrived
	 * one: every hosted style provider's URL carries an API key alongside something else.
	 */
	@Test
	void escapesTheConfiguredStyleRatherThanWritingItIntoTheAttributeRaw() {
		String html = pageWith("https://tiles.example/style.json?key=demo&lang=en").html();

		assertThat(html).contains("content=\"https://tiles.example/style.json?key=demo&amp;lang=en\"");
	}

	private static TrackingBootstrapPage pageWith(String mapStyleUrl) {
		return new TrackingBootstrapPage(new TrackingLinkProperties(Map.of(1, "irrelevant"), 1, true, mapStyleUrl));
	}

}
