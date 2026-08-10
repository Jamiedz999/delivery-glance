package com.deliveryglance.courier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.MutableClock;
import com.deliveryglance.TestClockConfiguration;
import com.deliveryglance.TimeControlledIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The Courier's own workspace, driven end to end: On Duty on one side, an explicitly started
 * Location Sharing Session on the other, and the freshness the two produce together.
 */
@TimeControlledIntegrationTest
class CourierApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MutableClock clock;

	private BrowserLikeClient client;

	@BeforeEach
	void signInAsCourier() throws Exception {
		this.clock.set(TestClockConfiguration.START);
		this.client = new BrowserLikeClient(this.mockMvc);
		this.client.signIn(DemoAccounts.COURIER_EMAIL, DemoAccounts.COURIER_PASSWORD);
		// The Courier record is durable, so each test states the duty and sharing it starts from
		// rather than inheriting whatever the previous one left behind.
		this.client.send(delete("/api/couriers/me/location-sharing"));
		setDuty(false);
	}

	@Test
	void changesOnDutyWithoutStartingLocationSharing() throws Exception {
		MockHttpServletResponse response = setDuty(true);

		assertThat(response.getStatus()).isEqualTo(200);
		String body = response.getContentAsString();
		assertThat((String) JsonPath.read(body, "$.displayName")).isEqualTo(DemoAccounts.COURIER_DISPLAY_NAME);
		assertThat((Boolean) JsonPath.read(body, "$.onDuty")).isTrue();
		assertThat((String) JsonPath.read(body, "$.onDutyChangedAt")).isEqualTo(TestClockConfiguration.START.toString());
		assertThat(JsonPath.<Object>read(body, "$.sharing")).isNull();
		assertThat((String) JsonPath.read(body, "$.location.freshness")).isEqualTo("UNAVAILABLE");
	}

	@Test
	void keepsOnDutyAcrossSignOut() throws Exception {
		setDuty(true);

		this.client.signOut();
		this.client.signIn(DemoAccounts.COURIER_EMAIL, DemoAccounts.COURIER_PASSWORD);

		assertThat((Boolean) JsonPath.read(me(), "$.onDuty")).isTrue();
	}

	@Test
	void leavesOnDutyAloneWhenLocationSharingStartsAndStops() throws Exception {
		setDuty(true);

		startSharing();
		assertThat((Boolean) JsonPath.read(me(), "$.onDuty")).isTrue();

		this.client.send(delete("/api/couriers/me/location-sharing"));
		assertThat((Boolean) JsonPath.read(me(), "$.onDuty")).isTrue();
	}

	@Test
	void issuesAReportingSecretOnceAndNeverReturnsItAgain() throws Exception {
		MockHttpServletResponse response = this.client.send(post("/api/couriers/me/location-sharing"));

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(response.getHeader("Cache-Control")).contains("no-store");
		String secret = JsonPath.read(response.getContentAsString(), "$.reportingSecret");
		assertThat(secret).isNotBlank();

		String me = me();
		assertThat(me).doesNotContain("reportingSecret").doesNotContain(secret);
		assertThat((String) JsonPath.read(me, "$.sharing.startedAt")).isEqualTo(TestClockConfiguration.START.toString());
	}

	@Test
	void acceptsTheNewestReadingAndCallsItLive() throws Exception {
		Session session = startSharing();

		MockHttpServletResponse response = report(session, 12.0, TestClockConfiguration.START);

		assertThat(response.getStatus()).isEqualTo(200);
		String body = response.getContentAsString();
		assertThat((String) JsonPath.read(body, "$.outcome")).isEqualTo("ACCEPTED");
		assertThat((String) JsonPath.read(body, "$.location.freshness")).isEqualTo("LIVE");
		assertThat((Double) JsonPath.read(body, "$.location.accuracyMetres")).isEqualTo(12.0);
		// The response tells the browser what the position now is, never where it is.
		assertThat(body).doesNotContain("51.5074").doesNotContain("-0.1278");

		assertThat((String) JsonPath.read(me(), "$.location.freshness")).isEqualTo("LIVE");
	}

	@Test
	void agesTheStoredReadingFromLiveThroughDelayedToUnavailable() throws Exception {
		Session session = startSharing();
		report(session, 12.0, TestClockConfiguration.START);

		this.clock.advance(Duration.ofSeconds(31));
		assertThat((String) JsonPath.read(me(), "$.location.freshness")).isEqualTo("DELAYED");

		this.clock.advance(Duration.ofSeconds(90));
		String unavailable = me();
		assertThat((String) JsonPath.read(unavailable, "$.location.freshness")).isEqualTo("UNAVAILABLE");
		assertThat(JsonPath.<Object>read(unavailable, "$.location.recordedAt")).isNull();
		// Sharing itself has not ended: the Courier may still produce a new usable reading.
		assertThat(JsonPath.<Object>read(unavailable, "$.sharing")).isNotNull();
	}

	/**
	 * The state a restart leaves behind, reached the only other way it can be: the durable session
	 * row exists and the process memory behind it holds nothing. Nothing in PostgreSQL can refill
	 * it, so the honest answer is Unavailable rather than a position from before the restart.
	 */
	@Test
	void reportsUnavailableWhileASessionHasProducedNoStoredPosition() throws Exception {
		startSharing();

		String me = me();

		assertThat(JsonPath.<Object>read(me, "$.sharing")).isNotNull();
		assertThat((String) JsonPath.read(me, "$.location.freshness")).isEqualTo("UNAVAILABLE");
		assertThat(JsonPath.<Object>read(me, "$.location.recordedAt")).isNull();
	}

	@Test
	void keepsCurrentLocationWhenAPoorReadingArrives() throws Exception {
		Session session = startSharing();
		report(session, 12.0, TestClockConfiguration.START);

		this.clock.advance(Duration.ofSeconds(10));
		MockHttpServletResponse response = report(session, 250.0, this.clock.instant());

		String body = response.getContentAsString();
		assertThat((String) JsonPath.read(body, "$.outcome")).isEqualTo("REJECTED_LOW_ACCURACY");
		assertThat((String) JsonPath.read(body, "$.location.recordedAt"))
			.isEqualTo(TestClockConfiguration.START.toString());
	}

	@Test
	void forgetsTheCoordinatesAsSoonAsSharingStops() throws Exception {
		Session session = startSharing();
		report(session, 12.0, TestClockConfiguration.START);

		assertThat(this.client.send(delete("/api/couriers/me/location-sharing")).getStatus()).isEqualTo(204);

		String me = me();
		assertThat(JsonPath.<Object>read(me, "$.sharing")).isNull();
		assertThat((String) JsonPath.read(me, "$.location.freshness")).isEqualTo("UNAVAILABLE");
		// The reporting secret cannot bring the position back either.
		assertThat(report(session, 12.0, this.clock.instant()).getStatus()).isEqualTo(409);
	}

	@Test
	void forgetsTheCoordinatesOnSignOut() throws Exception {
		Session session = startSharing();
		report(session, 12.0, TestClockConfiguration.START);

		this.client.signOut();
		this.client.signIn(DemoAccounts.COURIER_EMAIL, DemoAccounts.COURIER_PASSWORD);

		String me = me();
		assertThat(JsonPath.<Object>read(me, "$.sharing")).isNull();
		assertThat((String) JsonPath.read(me, "$.location.freshness")).isEqualTo("UNAVAILABLE");
	}

	@Test
	void refusesAReportFromAnEarlierSession() throws Exception {
		Session first = startSharing();
		Session second = startSharing();

		MockHttpServletResponse response = report(first, 12.0, TestClockConfiguration.START);

		assertThat(response.getStatus()).isEqualTo(409);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("location-sharing-ended");
		assertThat(report(second, 12.0, TestClockConfiguration.START).getStatus()).isEqualTo(200);
	}

	@Test
	void refusesAReportThatCannotProveTheSessionSecret() throws Exception {
		Session session = startSharing();

		MockHttpServletResponse response = report(new Session(session.generation(), "not-the-issued-secret"), 12.0,
				TestClockConfiguration.START);

		assertThat(response.getStatus()).isEqualTo(409);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("location-sharing-ended");
	}

	@Test
	void refusesAReportForAnUnknownGeneration() throws Exception {
		Session session = startSharing();

		MockHttpServletResponse response = report(new Session(UUID.randomUUID(), session.reportingSecret()), 12.0,
				TestClockConfiguration.START);

		assertThat(response.getStatus()).isEqualTo(409);
	}

	@Test
	void refusesAStructurallyImpossiblePosition() throws Exception {
		Session session = startSharing();

		MockHttpServletResponse response = this.client.send(post("/api/couriers/me/location-reports")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"generation":"%s","reportingSecret":"%s","longitude":-0.1278,"latitude":91.0,
					 "accuracyMetres":12.0,"recordedAt":"%s"}
					""".formatted(session.generation(), session.reportingSecret(), TestClockConfiguration.START)));

		assertThat(response.getStatus()).isEqualTo(400);
		String body = response.getContentAsString();
		assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("invalid-request");
		assertThat(JsonPath.<java.util.List<String>>read(body, "$.errors[*].field")).contains("latitude");
	}

	private MockHttpServletResponse setDuty(boolean onDuty) throws Exception {
		return this.client.send(put("/api/couriers/me/duty").contentType(MediaType.APPLICATION_JSON)
			.content("{\"onDuty\":%s}".formatted(onDuty)));
	}

	private String me() throws Exception {
		MockHttpServletResponse response = this.client.send(get("/api/couriers/me"));
		assertThat(response.getStatus()).isEqualTo(200);
		return response.getContentAsString();
	}

	private Session startSharing() throws Exception {
		MockHttpServletResponse response = this.client.send(post("/api/couriers/me/location-sharing"));
		assertThat(response.getStatus()).isEqualTo(201);
		String body = response.getContentAsString();
		return new Session(UUID.fromString(JsonPath.read(body, "$.generation")),
				JsonPath.read(body, "$.reportingSecret"));
	}

	private MockHttpServletResponse report(Session session, double accuracyMetres, Instant recordedAt)
			throws Exception {
		return this.client.send(post("/api/couriers/me/location-reports").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"generation":"%s","reportingSecret":"%s","longitude":-0.1278,"latitude":51.5074,
					 "accuracyMetres":%s,"recordedAt":"%s"}
					""".formatted(session.generation(), session.reportingSecret(), accuracyMetres, recordedAt)));
	}

	private record Session(UUID generation, String reportingSecret) {
	}

}
