package com.deliveryglance.trackinglink;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.TimeControlledIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The promise the whole Tracking Link design rests on: the raw capability exists in a Copy response
 * and in the Recipient's own browser, and nowhere else. These tests look for it in the two places it
 * would end up without anyone deciding to put it there — the schema and the application log.
 *
 * <p>Modelled on {@code LocationPrivacyTest}, and for the same reason: a rule that only lives in a
 * code review is a rule that comes back.
 */
@TimeControlledIntegrationTest
class TrackingLinkPrivacyTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	/** Well formed, and no link was ever derived from it: the shape a real guess would have. */
	private static final String REJECTED_TOKEN = "bm8tbGluay13YXMtZXZlci1kZXJpdmVkLWZyb20tdGg";

	/** Sent inside a body the message converter cannot parse, so only the exception ever holds it. */
	private static final String UNREADABLE_BODY_TOKEN = "dGhpcy1vbmUtbmV2ZXItcmVhY2hlZC10aGUtbW9kdWw";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	private BrowserLikeClient dispatcher;

	@BeforeEach
	void signInAsDispatcher() throws Exception {
		this.dispatcher = new BrowserLikeClient(this.mockMvc);
		this.dispatcher.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);
	}

	/**
	 * There is no column anywhere that a raw token or a complete Tracking URL could be written to,
	 * so no future change can start storing one without also changing the schema and this test.
	 */
	@Test
	void hasNoColumnAnywhereThatCouldHoldARawTokenOrATrackingUrl() {
		List<String> columns = this.jdbcClient.sql("""
				SELECT table_name || '.' || column_name
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND (column_name LIKE '%token%' OR column_name LIKE '%secret%'
				       OR column_name LIKE '%url%' OR column_name LIKE '%capability%'
				       OR column_name LIKE '%link%')
				ORDER BY 1
				""").query(String.class).list();

		// Every one of these is a verifier, an internal identity or a foreign key to one. None can be
		// turned back into something a browser could present.
		assertThat(columns).containsExactly("courier_location_sharing.reporting_secret_verifier",
				"tracking_grant.link_id", "tracking_grant.secret_verifier", "tracking_link.link_id",
				"tracking_link.token_verifier", "tracking_link_copy.link_id");
	}

	@Test
	void storesNeitherTheTokenNorTheGrantSecretOfAnEstablishedSession() throws Exception {
		String deliveryId = createDelivery();
		String token = tokenOf(copiedUrl(deliveryId));
		BrowserLikeClient holder = holderWhoOpened();
		String grantCookie = grantCookieOf(exchange(holder, token));

		String rows = this.jdbcClient.sql("""
				SELECT l.link_id || ' ' || l.token_verifier || ' ' || coalesce(g.secret_verifier, '') || ' '
				       || coalesce(c.id::text, '')
				FROM tracking_link l
				LEFT JOIN tracking_grant g ON g.link_id = l.link_id
				LEFT JOIN tracking_link_copy c ON c.link_id = l.link_id
				""").query(String.class).list().toString();

		assertThat(rows).doesNotContain(token).doesNotContain(grantCookie);
	}

	/**
	 * Every path that touches a token in one request: issue, Copy, a good exchange, a refused one,
	 * and one that blows up in the message converter before any of this module's code runs.
	 */
	@Test
	void keepsRawTokensOutOfTheApplicationLog() throws Exception {
		// Capture starts before the Delivery is created, because creation is the first moment a token
		// exists at all: it is derived there so its verifier can be stored, and the spec names
		// creation as one of the paths that must not log it.
		ListAppender<ILoggingEvent> captured = captureApplicationLog();
		String token;
		String grantCookie;
		try {
			String deliveryId = createDelivery();
			token = tokenOf(copiedUrl(deliveryId));
			BrowserLikeClient holder = holderWhoOpened();
			grantCookie = grantCookieOf(exchange(holder, token));
			assertThat(holder.send(get("/api/tracking/snapshot")).getStatus()).isEqualTo(200);

			// A rejected guess: the value the caller sent is a secret to them, and it must not be
			// written down either — a log of rejected tokens is a log of near misses.
			assertThat(exchange(holderWhoOpened(), REJECTED_TOKEN).getStatus()).isEqualTo(404);
			// The parse-failure path, carrying a token in a body the converter cannot read, so the
			// exception it throws holds the token rather than this module ever seeing it.
			assertThat(holderWhoOpened()
				.send(post("/api/tracking-session").contentType(MediaType.APPLICATION_JSON)
					.content("{\"token\":\"%s\"".formatted(UNREADABLE_BODY_TOKEN)))
				.getStatus()).isEqualTo(404);
		}
		finally {
			rootLogger().detachAppender(captured);
		}

		assertThat(captured.list).allSatisfy((event) -> assertThat(describe(event)).doesNotContain(token)
			.doesNotContain(grantCookie)
			.doesNotContain(REJECTED_TOKEN)
			.doesNotContain(UNREADABLE_BODY_TOKEN)
			.doesNotContain("/track#"));
	}

	/**
	 * A verifier is a one-way function of the token, so even a database and a log side by side
	 * cannot produce a working link. Asserting it here keeps the two halves of that claim together.
	 */
	@Test
	void storesOnlyDigestsThatCannotBeTurnedBackIntoACapability() throws Exception {
		String token = tokenOf(copiedUrl(createDelivery()));
		exchange(holderWhoOpened(), token);

		List<String> verifiers = this.jdbcClient
			.sql("SELECT token_verifier FROM tracking_link UNION ALL SELECT secret_verifier FROM tracking_grant")
			.query(String.class)
			.list();

		assertThat(verifiers).isNotEmpty().allSatisfy((verifier) -> assertThat(verifier).matches("^[0-9a-f]{64}$"));
	}

	private static String describe(ILoggingEvent event) {
		return event.getFormattedMessage()
				+ ((event.getThrowableProxy() != null) ? " " + event.getThrowableProxy().getMessage() : "");
	}

	/** Captures at the level the application actually runs at, as {@code LocationPrivacyTest} does. */
	private ListAppender<ILoggingEvent> captureApplicationLog() {
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		rootLogger().addAppender(appender);
		return appender;
	}

	private static Logger rootLogger() {
		return (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
	}

	private BrowserLikeClient holderWhoOpened() throws Exception {
		BrowserLikeClient holder = new BrowserLikeClient(this.mockMvc);
		holder.send(get("/track"));
		return holder;
	}

	private MockHttpServletResponse exchange(BrowserLikeClient holder, String token) throws Exception {
		return holder.send(post("/api/tracking-session").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"%s\"}".formatted(token)));
	}

	private static String grantCookieOf(MockHttpServletResponse response) {
		return response.getCookie(TrackingGrants.COOKIE_NAME).getValue();
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

	private String createDelivery() throws Exception {
		MockHttpServletResponse response = this.dispatcher
			.send(post("/api/deliveries").contentType(MediaType.APPLICATION_JSON).content("""
					{"reference":"DG-PRIV-%04d",
					 "pickup":{"addressLabel":"1 Pickup Street","latitude":51.5074,"longitude":-0.1278},
					 "handoff":{"addressLabel":"2 Handoff Road","latitude":51.5090,"longitude":-0.1300}}
					""".formatted(SEQUENCE.incrementAndGet())));
		assertThat(response.getStatus()).isEqualTo(201);
		return JsonPath.<String>read(response.getContentAsString(), "$.id");
	}

}
