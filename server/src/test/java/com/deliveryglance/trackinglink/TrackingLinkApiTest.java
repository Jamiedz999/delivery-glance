package com.deliveryglance.trackinglink;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.MutableClock;
import com.deliveryglance.TimeControlledIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The Tracking Link through the API a Dispatcher and a Link Holder actually use.
 *
 * <p>The holder is a second {@link BrowserLikeClient} with no Internal Account session, so nothing
 * here can pass because the test happened to be signed in.
 */
@TimeControlledIntegrationTest
class TrackingLinkApiTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	private static final String UNAVAILABLE_CODE = "tracking-link-unavailable";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private MutableClock clock;

	private BrowserLikeClient dispatcher;

	@BeforeEach
	void signInAsDispatcher() throws Exception {
		this.clock.set(com.deliveryglance.TestClockConfiguration.START);
		this.dispatcher = new BrowserLikeClient(this.mockMvc);
		this.dispatcher.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);
	}

	@Test
	void givesEveryNewDeliveryExactlyOneLink() throws Exception {
		UUID deliveryId = UUID.fromString(createDelivery());

		assertThat(this.jdbcClient.sql("SELECT count(*) FROM tracking_link WHERE delivery_id = :id")
			.param("id", deliveryId)
			.query(Integer.class)
			.single()).isEqualTo(1);
	}

	@Test
	void returnsTheSameLinkEveryTimeTheDispatcherCopiesIt() throws Exception {
		String deliveryId = createDelivery();

		assertThat(copiedUrl(deliveryId)).isEqualTo(copiedUrl(deliveryId));
	}

	@Test
	void buildsTheLinkAsAFragmentSoTheTokenNeverReachesTheServerInARequest() throws Exception {
		String url = copiedUrl(createDelivery());

		assertThat(url).matches("^/track#t=[A-Za-z0-9_-]{43}$");
	}

	@Test
	void refusesCopyToEveryoneExceptADispatcher() throws Exception {
		String deliveryId = createDelivery();

		BrowserLikeClient courier = new BrowserLikeClient(this.mockMvc);
		courier.signIn(DemoAccounts.COURIER_EMAIL, DemoAccounts.COURIER_PASSWORD);
		assertThat(courier.send(post("/api/deliveries/{id}/tracking-link/copy", deliveryId)).getStatus())
			.isEqualTo(403);

		BrowserLikeClient stranger = new BrowserLikeClient(this.mockMvc);
		stranger.send(get("/api/system"));
		assertThat(stranger.send(post("/api/deliveries/{id}/tracking-link/copy", deliveryId)).getStatus())
			.isEqualTo(401);
	}

	@Test
	void recordsWhoCopiedAndWhenAndNothingElse() throws Exception {
		String deliveryId = createDelivery();
		copiedUrl(deliveryId);

		List<String> copies = this.jdbcClient.sql("""
				SELECT a.email || ' ' || c.copied_at
				FROM tracking_link_copy c
				JOIN tracking_link l ON l.link_id = c.link_id
				JOIN internal_account a ON a.id = c.actor_account_id
				WHERE l.delivery_id = :id
				""").param("id", UUID.fromString(deliveryId)).query(String.class).list();

		assertThat(copies).singleElement().asString().startsWith(DemoAccounts.DISPATCHER_EMAIL);
	}

	@Test
	void exchangesAValidTokenForAGrantThatReadsTheDelivery() throws Exception {
		String deliveryId = createDelivery();
		String reference = referenceOf(deliveryId);
		BrowserLikeClient holder = holderWhoOpened();

		assertThat(exchange(holder, tokenOf(copiedUrl(deliveryId))).getStatus()).isEqualTo(204);

		MockHttpServletResponse snapshot = holder.send(get("/api/tracking/snapshot"));
		assertThat(snapshot.getStatus()).isEqualTo(200);
		assertThat((String) JsonPath.read(snapshot.getContentAsString(), "$.deliveryReference")).isEqualTo(reference);
	}

	@Test
	void marksTheGrantCookieHttpOnlyAndHostOnlyAndLaxSoItSurvivesALinkFollowedFromAMessage() throws Exception {
		BrowserLikeClient holder = holderWhoOpened();

		MockHttpServletResponse response = exchange(holder, tokenOf(copiedUrl(createDelivery())));

		assertThat(setCookieFor(response, TrackingGrants.COOKIE_NAME)).contains("HttpOnly")
			.contains("SameSite=Lax")
			.contains("Path=/")
			.doesNotContain("Domain=");
	}

	private static String setCookieFor(MockHttpServletResponse response, String name) {
		return response.getHeaders("Set-Cookie")
			.stream()
			.filter((header) -> header.startsWith(name + "="))
			.findFirst()
			.orElseThrow();
	}

	@Test
	void answersUnknownMalformedAndExpiredTokensWithOneIndistinguishableResponse() throws Exception {
		String expiredToken = tokenOf(copiedUrl(createDelivery()));
		this.clock.advance(TrackingLinks.LIFETIME.plusSeconds(1));

		// Exactly the right shape — 43 base64url characters, 256 bits — but no link was ever derived
		// from it, so this is the "unknown" case rather than a second "malformed" one.
		String unknown = bodyOfRefusal("bm8tbGluay13YXMtZXZlci1kZXJpdmVkLWZyb20tdGg");
		String malformed = bodyOfRefusal("not-a-token");
		String expired = bodyOfRefusal(expiredToken);

		assertThat(unknown).isEqualTo(malformed).isEqualTo(expired);
		assertThat((String) JsonPath.read(unknown, "$.code")).isEqualTo(UNAVAILABLE_CODE);
	}

	@Test
	void answersAnAbsentOrUnreadableBodyWithTheSameUnavailableResponse() throws Exception {
		BrowserLikeClient holder = holderWhoOpened();

		MockHttpServletResponse response = holder
			.send(post("/api/tracking-session").contentType(MediaType.APPLICATION_JSON).content("{"));

		assertThat(response.getStatus()).isEqualTo(404);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo(UNAVAILABLE_CODE);
	}

	@Test
	void stopsAcceptingALinkSevenDaysAfterItWasIssued() throws Exception {
		String token = tokenOf(copiedUrl(createDelivery()));

		this.clock.advance(TrackingLinks.LIFETIME.minusSeconds(1));
		assertThat(exchange(holderWhoOpened(), token).getStatus()).isEqualTo(204);

		this.clock.advance(Duration.ofSeconds(2));
		assertThat(exchange(holderWhoOpened(), token).getStatus()).isEqualTo(404);
	}

	@Test
	void stopsAcceptingALinkTwentyFourHoursAfterTheDeliveryIsCancelled() throws Exception {
		String deliveryId = createDelivery();
		String token = tokenOf(copiedUrl(deliveryId));
		cancel(deliveryId);

		this.clock.advance(Duration.ofHours(23));
		assertThat(exchange(holderWhoOpened(), token).getStatus()).isEqualTo(204);

		this.clock.advance(Duration.ofHours(2));
		assertThat(exchange(holderWhoOpened(), token).getStatus()).isEqualTo(404);
	}

	/**
	 * The grant's own expiry was fixed while the Delivery was still running. Ending the Delivery
	 * shortens the link, and the already-open page has to lose access on its next read rather than
	 * keeping the longer window it was issued.
	 */
	@Test
	void withdrawsAnEstablishedGrantWhenTheDeliveryEndsAndItsGracePeriodRunsOut() throws Exception {
		String deliveryId = createDelivery();
		BrowserLikeClient holder = holderWhoOpened();
		assertThat(exchange(holder, tokenOf(copiedUrl(deliveryId))).getStatus()).isEqualTo(204);

		cancel(deliveryId);
		this.clock.advance(Duration.ofHours(25));

		MockHttpServletResponse snapshot = holder.send(get("/api/tracking/snapshot"));
		assertThat(snapshot.getStatus()).isEqualTo(404);
		assertThat((String) JsonPath.read(snapshot.getContentAsString(), "$.code")).isEqualTo(UNAVAILABLE_CODE);
	}

	/**
	 * The Delivered half of the terminal rule. Cancelled is covered above; both come from one
	 * {@code next_state IN (...)} list, and if DELIVERED fell out of it the Cancelled test and the
	 * expiry unit test would both still pass while a delivered Delivery stayed readable for a week.
	 */
	@Test
	void stopsAcceptingALinkTwentyFourHoursAfterTheDeliveryIsDelivered() throws Exception {
		String deliveryId = createDelivery();
		String token = tokenOf(copiedUrl(deliveryId));
		deliver(deliveryId);

		this.clock.advance(Duration.ofHours(23));
		assertThat(exchange(holderWhoOpened(), token).getStatus()).isEqualTo(204);

		this.clock.advance(Duration.ofHours(2));
		assertThat(exchange(holderWhoOpened(), token).getStatus()).isEqualTo(404);
	}

	/**
	 * The instant the link expires, exactly. This is the one moment where the application's rule and
	 * the database's disagreed: treating the expiry instant as still valid meant writing a grant
	 * whose expires_at equalled its established_at, which the CHECK constraint refuses — so a link
	 * exchanged on the tick produced a 500 with a stack trace instead of the one Unavailable
	 * response every other failure gets.
	 */
	@Test
	void refusesTheExchangeOnTheExpiryInstantItselfRatherThanFailingOnIt() throws Exception {
		String token = tokenOf(copiedUrl(createDelivery()));

		this.clock.advance(TrackingLinks.LIFETIME);

		MockHttpServletResponse response = exchange(holderWhoOpened(), token);
		assertThat(response.getStatus()).isEqualTo(404);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo(UNAVAILABLE_CODE);
	}

	/**
	 * The grant's own bound, checked before the link is consulted at all. A Delivery that never
	 * reaches a terminal state gives the grant the full seven days and no more.
	 */
	@Test
	void stopsHonouringAnEstablishedGrantOnceItsOwnBoundPasses() throws Exception {
		BrowserLikeClient holder = holderWhoOpened();
		assertThat(exchange(holder, tokenOf(copiedUrl(createDelivery()))).getStatus()).isEqualTo(204);
		assertThat(holder.send(get("/api/tracking/snapshot")).getStatus()).isEqualTo(200);

		this.clock.advance(TrackingLinks.LIFETIME.plusSeconds(1));

		MockHttpServletResponse snapshot = holder.send(get("/api/tracking/snapshot"));
		assertThat(snapshot.getStatus()).isEqualTo(404);
		assertThat((String) JsonPath.read(snapshot.getContentAsString(), "$.code")).isEqualTo(UNAVAILABLE_CODE);
		// The cookie is cleared so the browser stops presenting something that can never work again.
		assertThat(snapshot.getCookie(TrackingGrants.COOKIE_NAME).getMaxAge()).isZero();
	}

	/**
	 * Production defaults to Secure and the local Compose demo is what opts out, so the default
	 * configuration the tests run under is the one that has to carry the flag.
	 */
	@Test
	void marksTheGrantCookieSecureUnderTheDefaultConfiguration() throws Exception {
		MockHttpServletResponse response = exchange(holderWhoOpened(), tokenOf(copiedUrl(createDelivery())));

		assertThat(setCookieFor(response, TrackingGrants.COOKIE_NAME)).contains("Secure");
	}

	@Test
	void refusesASnapshotToABrowserHoldingNoGrant() throws Exception {
		BrowserLikeClient stranger = holderWhoOpened();

		assertThat(stranger.send(get("/api/tracking/snapshot")).getStatus()).isEqualTo(404);
	}

	/**
	 * The two authorization systems do not lend each other anything. Both halves matter: a
	 * Dispatcher session is not a Recipient session, and a Tracking grant is not an internal one.
	 */
	@Test
	void keepsInternalSessionsAndTrackingGrantsFromStandingInForEachOther() throws Exception {
		String deliveryId = createDelivery();

		assertThat(this.dispatcher.send(get("/api/tracking/snapshot")).getStatus()).isEqualTo(404);

		BrowserLikeClient holder = holderWhoOpened();
		exchange(holder, tokenOf(copiedUrl(deliveryId)));
		assertThat(holder.send(get("/api/deliveries")).getStatus()).isEqualTo(401);
		assertThat(holder.send(get("/api/deliveries/{id}", deliveryId)).getStatus()).isEqualTo(401);
		assertThat(holder.send(get("/api/couriers/me")).getStatus()).isEqualTo(401);
	}

	@Test
	void servesAGenericBootstrapPageThatCannotActivateOrConsumeALink() throws Exception {
		String deliveryId = createDelivery();
		String token = tokenOf(copiedUrl(deliveryId));
		BrowserLikeClient previewer = new BrowserLikeClient(this.mockMvc);

		// What a link preview, a mail scanner or a prefetcher would do, several times over.
		for (int visit = 0; visit < 3; visit++) {
			assertThat(previewer.send(get("/track")).getStatus()).isEqualTo(200);
			assertThat(previewer.send(head("/track")).getStatus()).isEqualTo(200);
			assertThat(previewer.send(get("/track?t={token}", token)).getStatus()).isEqualTo(200);
		}

		assertThat(this.jdbcClient.sql("SELECT count(*) FROM tracking_grant").query(Integer.class).single()).isZero();
		assertThat(exchange(holderWhoOpened(), token).getStatus()).isEqualTo(204);
	}

	@Test
	void tellsNothingAboutAnyDeliveryInTheBootstrapPage() throws Exception {
		String deliveryId = createDelivery();
		String reference = referenceOf(deliveryId);
		String token = tokenOf(copiedUrl(deliveryId));

		String page = new BrowserLikeClient(this.mockMvc).send(get("/track")).getContentAsString();

		assertThat(page).doesNotContain(reference).doesNotContain(deliveryId).doesNotContain(token);
	}

	@Test
	void sendsTheAgreedCacheReferrerIndexingAndContentHeadersOnEveryTrackingResponse() throws Exception {
		String deliveryId = createDelivery();
		BrowserLikeClient holder = holderWhoOpened();
		String token = tokenOf(copiedUrl(deliveryId));

		assertProtectedHeaders(new BrowserLikeClient(this.mockMvc).send(get("/track")));
		assertProtectedHeaders(this.dispatcher.send(post("/api/deliveries/{id}/tracking-link/copy", deliveryId)));
		assertProtectedHeaders(exchange(holder, token));
		assertProtectedHeaders(holder.send(get("/api/tracking/snapshot")));
		assertProtectedHeaders(exchange(holderWhoOpened(), "not-a-token"));
	}

	/**
	 * A request Spring Security refuses never reaches a handler, so for as long as the handlers were
	 * the things applying these headers, a rejected CSRF token produced a tracking response with none
	 * of them. "All tracking responses" has to include the ones the application never got to answer.
	 */
	@Test
	void sendsTheHeadersEvenOnResponsesTheSecurityChainWritesWithoutReachingAHandler() throws Exception {
		BrowserLikeClient holder = holderWhoOpened();

		MockHttpServletResponse rejected = holder.sendWithoutCsrfHeader(post("/api/tracking-session")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"%s\"}".formatted(tokenOf(copiedUrl(createDelivery())))));

		assertThat(rejected.getStatus()).isEqualTo(403);
		assertProtectedHeaders(rejected);
	}

	/**
	 * The JSON routes render nothing, so they say nothing may be rendered. The bootstrap page needs a
	 * wider policy and carries its own.
	 */
	@Test
	void sendsAnInertContentSecurityPolicyOnTheTrackingApiResponsesToo() throws Exception {
		BrowserLikeClient holder = holderWhoOpened();
		exchange(holder, tokenOf(copiedUrl(createDelivery())));

		String policy = holder.send(get("/api/tracking/snapshot")).getHeader("Content-Security-Policy");
		assertThat(policy).isEqualTo(
				"default-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
	}

	@Test
	void locksTheBootstrapPageDownToItsOwnInlinedScript() throws Exception {
		MockHttpServletResponse response = new BrowserLikeClient(this.mockMvc).send(get("/track"));

		String policy = response.getHeader("Content-Security-Policy");
		assertThat(policy).contains("default-src 'none'")
			.contains("connect-src 'self'")
			.contains("frame-ancestors 'none'")
			.containsPattern("script-src 'sha256-[A-Za-z0-9+/=]+'");
		assertThat(policy).doesNotContain("'unsafe-inline'").doesNotContain("'unsafe-eval'");
	}

	/**
	 * The hash has to match the bytes actually served, and nothing else in the suite would notice if
	 * it did not: jsdom does not enforce CSP, so a wrong hash would pass every frontend test and then
	 * silently refuse to run the bootstrap in a real browser. This recomputes it from the response.
	 */
	@Test
	void pinsTheScriptAndStyleItActuallyServesRatherThanAHashWrittenDownBesideThem() throws Exception {
		MockHttpServletResponse response = new BrowserLikeClient(this.mockMvc).send(get("/track"));
		String page = response.getContentAsString();
		String policy = response.getHeader("Content-Security-Policy");

		assertThat(policy).contains("script-src 'sha256-%s'".formatted(sha256Base64(between(page, "<script>", "</script>"))))
			.contains("style-src 'sha256-%s'".formatted(sha256Base64(between(page, "<style>", "</style>"))));
	}

	private static String between(String source, String open, String close) {
		int start = source.indexOf(open) + open.length();
		int end = source.indexOf(close, start);
		assertThat(end).isGreaterThan(start);
		return source.substring(start, end);
	}

	private static String sha256Base64(String source) throws NoSuchAlgorithmException {
		return Base64.getEncoder()
			.encodeToString(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
	}

	private void assertProtectedHeaders(MockHttpServletResponse response) {
		assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
		assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
		assertThat(response.getHeader("X-Robots-Tag")).isEqualTo("noindex, nofollow, nosnippet");
		assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
	}

	/** A browser that has loaded /track, and so holds the CSRF cookie the exchange needs. */
	private BrowserLikeClient holderWhoOpened() throws Exception {
		BrowserLikeClient holder = new BrowserLikeClient(this.mockMvc);
		holder.send(get("/track"));
		return holder;
	}

	private MockHttpServletResponse exchange(BrowserLikeClient holder, String token) throws Exception {
		return holder.send(post("/api/tracking-session").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"%s\"}".formatted(token)));
	}

	private String bodyOfRefusal(String token) throws Exception {
		MockHttpServletResponse response = exchange(holderWhoOpened(), token);
		assertThat(response.getStatus()).isEqualTo(404);
		return response.getContentAsString();
	}

	private static String tokenOf(String url) {
		return url.substring(url.indexOf("#t=") + 3);
	}

	private String copiedUrl(String deliveryId) throws Exception {
		MockHttpServletResponse response = this.dispatcher
			.send(post("/api/deliveries/{id}/tracking-link/copy", deliveryId));
		assertThat(response.getStatus()).isEqualTo(200);
		return JsonPath.read(response.getContentAsString(), "$.url");
	}

	private String referenceOf(String deliveryId) throws Exception {
		return JsonPath.read(this.dispatcher.send(get("/api/deliveries/{id}", deliveryId)).getContentAsString(),
				"$.reference");
	}

	/**
	 * Drives one Delivery all the way to Delivered through the real API, because that is the only
	 * thing that writes the terminal transition the link's grace period is derived from. The Courier
	 * is created here rather than reusing the seeded demo one: a Courier may hold only one active
	 * Assignment, and borrowing the shared account would couple this class to what every other test
	 * happens to have left behind.
	 */
	private void deliver(String deliveryId) throws Exception {
		int sequence = SEQUENCE.incrementAndGet();
		UUID courierId = UUID.randomUUID();
		String email = "courier-tracking-%d@delivery-glance.example".formatted(sequence);
		this.jdbcClient.sql("""
				INSERT INTO internal_account (id, email, password_hash, display_name, role, enabled)
				SELECT :id, :email, password_hash, :displayName, 'COURIER', true
				FROM internal_account WHERE email = :sourceEmail
				""")
			.param("id", courierId)
			.param("email", email)
			.param("displayName", "Tracking Courier %d".formatted(sequence))
			.param("sourceEmail", DemoAccounts.COURIER_EMAIL)
			.update();

		BrowserLikeClient courier = new BrowserLikeClient(this.mockMvc);
		courier.signIn(email, DemoAccounts.COURIER_PASSWORD);
		assertThat(courier
			.send(put("/api/couriers/me/duty").contentType(MediaType.APPLICATION_JSON).content("{\"onDuty\":true}"))
			.getStatus()).isEqualTo(200);
		MockHttpServletResponse started = courier.send(post("/api/couriers/me/location-sharing"));
		assertThat(courier.send(post("/api/couriers/me/location-reports").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"generation":"%s","reportingSecret":"%s","longitude":-0.1278,"latitude":51.5074,
					 "accuracyMetres":12.0,"recordedAt":"%s"}
					""".formatted(JsonPath.read(started.getContentAsString(), "$.generation"),
					JsonPath.<String>read(started.getContentAsString(), "$.reportingSecret"),
					this.clock.instant())))
			.getStatus()).isEqualTo(200);

		assertThat(this.dispatcher.send(post("/api/deliveries/{id}/assignment", deliveryId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"courierId":"%s","expectedVersion":0,"commandId":"%s"}
					""".formatted(courierId, UUID.randomUUID())))
			.getStatus()).isEqualTo(204);
		progress(courier, deliveryId, "pickup", 1);
		progress(courier, deliveryId, "handoff", 2);

		assertThat((String) JsonPath.read(this.dispatcher.send(get("/api/deliveries/{id}", deliveryId))
			.getContentAsString(), "$.state")).isEqualTo("DELIVERED");
	}

	private void progress(BrowserLikeClient courier, String deliveryId, String action, int expectedVersion)
			throws Exception {
		assertThat(courier.send(post("/api/couriers/me/deliveries/{id}/{action}", deliveryId, action)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"commandId":"%s","expectedVersion":%d}
					""".formatted(UUID.randomUUID(), expectedVersion)))
			.getStatus()).isEqualTo(204);
	}

	private void cancel(String deliveryId) throws Exception {
		MockHttpServletResponse response = this.dispatcher
			.send(post("/api/deliveries/{id}/cancel", deliveryId).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"expectedVersion":0,"reason":"NO_LONGER_REQUIRED","commandId":"%s"}
						""".formatted(UUID.randomUUID())));
		assertThat(response.getStatus()).isEqualTo(200);
	}

	private String createDelivery() throws Exception {
		MockHttpServletResponse response = this.dispatcher
			.send(post("/api/deliveries").contentType(MediaType.APPLICATION_JSON).content("""
					{"reference":"DG-TRK-%04d",
					 "pickup":{"addressLabel":"1 Pickup Street","latitude":51.5074,"longitude":-0.1278},
					 "handoff":{"addressLabel":"2 Handoff Road","latitude":51.5090,"longitude":-0.1300}}
					""".formatted(SEQUENCE.incrementAndGet())));
		assertThat(response.getStatus()).isEqualTo(201);
		return JsonPath.read(response.getContentAsString(), "$.id");
	}

}
