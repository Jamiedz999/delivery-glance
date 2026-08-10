package com.deliveryglance.location;

import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.TestClockConfiguration;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The promise the whole location design rests on: a Courier coordinate exists in memory for at most
 * two minutes and nowhere else. These tests look for it in the two places it would leak into
 * without anyone deciding to put it there — the schema and the application log.
 */
@TimeControlledIntegrationTest
class LocationPrivacyTest {

	private static final String LATITUDE = "51.5074";

	private static final String LONGITUDE = "-0.1278";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	private BrowserLikeClient client;

	@BeforeEach
	void signInAsCourier() throws Exception {
		this.client = new BrowserLikeClient(this.mockMvc);
		this.client.signIn(DemoAccounts.COURIER_EMAIL, DemoAccounts.COURIER_PASSWORD);
	}

	@Test
	void hasNoColumnAnywhereThatCouldHoldACourierPosition() {
		List<String> columns = this.jdbcClient.sql("""
				SELECT table_name || '.' || column_name
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND (column_name LIKE '%latitude%' OR column_name LIKE '%longitude%'
				       OR column_name LIKE '%coordinate%' OR column_name LIKE '%accuracy%'
				       OR column_name LIKE '%position%' OR column_name LIKE '%geo%')
				ORDER BY 1
				""").query(String.class).list();

		// The only coordinates the database holds are the Delivery's own pickup and handoff
		// addresses, which the Dispatcher typed in and which are not anybody's live position.
		assertThat(columns).containsExactly("delivery.handoff_latitude", "delivery.handoff_longitude",
				"delivery.pickup_latitude", "delivery.pickup_longitude");
	}

	@Test
	void storesNeitherTheCoordinatesNorTheReportingSecretOfAnAcceptedReport() throws Exception {
		Session session = startSharing();
		assertThat(outcomeOf(report(session, 12.0))).isEqualTo("ACCEPTED");

		String sharingRows = this.jdbcClient.sql("""
				SELECT courier_account_id || ' ' || generation || ' ' || reporting_secret_verifier || ' ' || started_at
				FROM courier_location_sharing
				""").query(String.class).list().toString();

		assertThat(sharingRows).doesNotContain(LATITUDE)
			.doesNotContain(LONGITUDE)
			.doesNotContain(session.reportingSecret());
	}

	@Test
	void keepsRawCoordinatesOutOfTheApplicationLog() throws Exception {
		Session session = startSharing();

		ListAppender<ILoggingEvent> captured = captureApplicationLog();
		try {
			assertThat(outcomeOf(report(session, 12.0))).isEqualTo("ACCEPTED");
			assertThat(outcomeOf(report(session, 250.0))).isEqualTo("REJECTED_LOW_ACCURACY");
			assertThat(reportOutOfRangeLatitude(session).getStatus()).isEqualTo(400);
		}
		finally {
			rootLogger().detachAppender(captured);
		}

		assertThat(captured.list).allSatisfy((event) -> assertThat(describe(event)).doesNotContain(LATITUDE)
			.doesNotContain(LONGITUDE)
			.doesNotContain(session.reportingSecret()));
	}

	private static String describe(ILoggingEvent event) {
		return event.getFormattedMessage()
				+ ((event.getThrowableProxy() != null) ? " " + event.getThrowableProxy().getMessage() : "");
	}

	/**
	 * Captures at the level the application actually runs at. Turning the whole framework up to
	 * TRACE would prove something else entirely: that a developer who asks a debug logger to print
	 * every request body gets every request body.
	 */
	private ListAppender<ILoggingEvent> captureApplicationLog() {
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		rootLogger().addAppender(appender);
		return appender;
	}

	private static Logger rootLogger() {
		return (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
	}

	private Session startSharing() throws Exception {
		MockHttpServletResponse response = this.client.send(post("/api/couriers/me/location-sharing"));
		assertThat(response.getStatus()).isEqualTo(201);
		String body = response.getContentAsString();
		return new Session(UUID.fromString(JsonPath.read(body, "$.generation")),
				JsonPath.read(body, "$.reportingSecret"));
	}

	private MockHttpServletResponse report(Session session, double accuracyMetres) throws Exception {
		return this.client.send(post("/api/couriers/me/location-reports").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"generation":"%s","reportingSecret":"%s","longitude":%s,"latitude":%s,
					 "accuracyMetres":%s,"recordedAt":"%s"}
					""".formatted(session.generation(), session.reportingSecret(), LONGITUDE, LATITUDE, accuracyMetres,
					TestClockConfiguration.START)));
	}

	private MockHttpServletResponse reportOutOfRangeLatitude(Session session) throws Exception {
		return this.client.send(post("/api/couriers/me/location-reports").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"generation":"%s","reportingSecret":"%s","longitude":%s,"latitude":991.5074,
					 "accuracyMetres":12.0,"recordedAt":"%s"}
					""".formatted(session.generation(), session.reportingSecret(), LONGITUDE,
					TestClockConfiguration.START)));
	}

	private static String outcomeOf(MockHttpServletResponse response) throws Exception {
		assertThat(response.getStatus()).isEqualTo(200);
		return JsonPath.read(response.getContentAsString(), "$.outcome");
	}

	private record Session(UUID generation, String reportingSecret) {
	}

}
