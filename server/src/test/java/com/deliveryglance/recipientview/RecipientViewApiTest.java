package com.deliveryglance.recipientview;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.MutableClock;
import com.deliveryglance.TestClockConfiguration;
import com.deliveryglance.TimeControlledIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The Recipient view as a Link Holder's browser actually receives it: real SQL, a real Assignment, a
 * real reported position and the real grant cookie.
 *
 * <p>{@code RecipientSnapshotsTest} owns the state matrix against fakes. What this class adds is
 * everything the fakes cannot be wrong about — that the reads join the right rows, that the two
 * minutes are counted from the device's measurement time through the whole stack, and that the JSON
 * that goes over the wire contains no identifier, no pickup address and no internal detail.
 */
@TimeControlledIntegrationTest
class RecipientViewApiTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	private static final String PICKUP_LABEL = "1 Depot Lane, Recipient View Test";

	private static final String HANDOFF_LABEL = "2 Handoff Road, Recipient View Test";

	private static final double COURIER_LATITUDE = 51.5081;

	private static final double COURIER_LONGITUDE = -0.1290;

	private static final double COURIER_ACCURACY_METRES = 14.0;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private MutableClock clock;

	private BrowserLikeClient dispatcher;

	@BeforeEach
	void signInAsDispatcher() throws Exception {
		this.clock.set(TestClockConfiguration.START);
		this.dispatcher = new BrowserLikeClient(this.mockMvc);
		this.dispatcher.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);
	}

	@Test
	void showsAWaitingRecipientTheirDeliveryAndNothingAboutACourier() throws Exception {
		String delivery = createDelivery();

		String snapshot = snapshotFor(delivery);

		assertThat(read(snapshot, "$.state")).isEqualTo("AWAITING_COURIER");
		assertThat(read(snapshot, "$.reference")).isEqualTo(referenceOf(delivery));
		assertThat(read(snapshot, "$.handoffAddressLabel")).isEqualTo(HANDOFF_LABEL);
		assertThat(read(snapshot, "$.courierDisplayName")).isNull();
		assertThat(read(snapshot, "$.map")).isNull();
	}

	@Test
	void namesTheCourierOnceAssignedButStillPutsNothingOnAMap() throws Exception {
		String delivery = createDelivery();
		Courier courier = onDutyCourierSharingLocation();
		assign(delivery, courier);

		String snapshot = snapshotFor(delivery);

		assertThat(read(snapshot, "$.state")).isEqualTo("ASSIGNED");
		assertThat(read(snapshot, "$.courierDisplayName")).isEqualTo(courier.displayName());
		assertThat(read(snapshot, "$.map")).isNull();
	}

	@Test
	void putsTheCourierOnTheMapWithItsAccuracyAndMeasurementTimeOnceInTransit() throws Exception {
		String delivery = createDelivery();
		Courier courier = onDutyCourierSharingLocation();
		assign(delivery, courier);
		progress(courier, delivery, "pickup", 1);

		String snapshot = snapshotFor(delivery);

		assertThat(read(snapshot, "$.state")).isEqualTo("IN_TRANSIT");
		assertThat(read(snapshot, "$.map.courier.latitude")).isEqualTo(COURIER_LATITUDE);
		assertThat(read(snapshot, "$.map.courier.longitude")).isEqualTo(COURIER_LONGITUDE);
		assertThat(read(snapshot, "$.map.courier.accuracyMetres")).isEqualTo(COURIER_ACCURACY_METRES);
		assertThat(read(snapshot, "$.map.courier.recordedAt")).isEqualTo(TestClockConfiguration.START.toString());
		// The handoff the Dispatcher typed, not a repeat of the pickup.
		assertThat(read(snapshot, "$.map.handoff.latitude")).isEqualTo(51.5090);
	}

	/**
	 * The two-minute usable limit, counted end to end from the device's measurement time. Only the
	 * server side of the freshness rule is here: Live through thirty seconds and Delayed after it
	 * are decided by the browser's own timer, because a page nobody reloads still has to age.
	 */
	@Test
	void keepsTheCourierMarkerForTwoMinutesAfterTheReadingAndNotASecondLonger() throws Exception {
		String delivery = createDelivery();
		Courier courier = onDutyCourierSharingLocation();
		assign(delivery, courier);
		progress(courier, delivery, "pickup", 1);

		this.clock.advance(Duration.ofMinutes(2));
		assertThat(read(snapshotFor(delivery), "$.map.courier.latitude")).isEqualTo(COURIER_LATITUDE);

		this.clock.advance(Duration.ofSeconds(1));
		String expired = snapshotFor(delivery);
		assertThat(read(expired, "$.map.courier")).isNull();
		assertThat(read(expired, "$.map.handoff.latitude")).isEqualTo(51.5090);
	}

	@Test
	void withdrawsTheCourierMarkerOnTheNextReadWhenSharingStops() throws Exception {
		String delivery = createDelivery();
		Courier courier = onDutyCourierSharingLocation();
		assign(delivery, courier);
		progress(courier, delivery, "pickup", 1);
		assertThat(read(snapshotFor(delivery), "$.map.courier.latitude")).isEqualTo(COURIER_LATITUDE);

		assertThat(courier.client().send(delete("/api/couriers/me/location-sharing")).getStatus()).isEqualTo(204);

		assertThat(read(snapshotFor(delivery), "$.map.courier")).isNull();
	}

	@Test
	void removesTheCourierAndEveryTraceOfLocationTheMomentTheDeliveryIsHandedOver() throws Exception {
		String delivery = createDelivery();
		Courier courier = onDutyCourierSharingLocation();
		assign(delivery, courier);
		progress(courier, delivery, "pickup", 1);
		progress(courier, delivery, "handoff", 2);

		String snapshot = snapshotFor(delivery);

		assertThat(read(snapshot, "$.state")).isEqualTo("DELIVERED");
		assertThat(read(snapshot, "$.completedAt")).isEqualTo(this.clock.instant().toString());
		assertThat(read(snapshot, "$.reference")).isEqualTo(referenceOf(delivery));
		assertThat(read(snapshot, "$.handoffAddressLabel")).isEqualTo(HANDOFF_LABEL);
		assertThat(read(snapshot, "$.courierDisplayName")).isNull();
		assertThat(read(snapshot, "$.map")).isNull();
		assertThat(snapshot).doesNotContain(courier.displayName()).doesNotContain(String.valueOf(COURIER_LATITUDE));
	}

	/**
	 * A cancelled link keeps working for its grace period, and what it shows in that window is the
	 * Reference, a generic outcome and who to ask. The Handoff Address goes: ADR 06 keeps the
	 * identifier a Recipient needs in order to phone the team, and drops the field that says where
	 * somebody lives, so a link left in a group chat stops describing anybody's home.
	 */
	@Test
	void showsACancelledDeliveryItsReferenceOutcomeTimeAndWhoToAskAndNoAddress() throws Exception {
		String delivery = createDelivery();
		cancel(delivery);

		String snapshot = snapshotFor(delivery);

		assertThat(read(snapshot, "$.state")).isEqualTo("CANCELLED");
		assertThat(read(snapshot, "$.completedAt")).isEqualTo(this.clock.instant().toString());
		assertThat(read(snapshot, "$.reference")).isEqualTo(referenceOf(delivery));
		assertThat(read(snapshot, "$.handoffAddressLabel")).isNull();
		assertThat(snapshot).doesNotContain(HANDOFF_LABEL).doesNotContain("NO_LONGER_REQUIRED");
	}

	/**
	 * The response is built for one audience rather than filtered down to it, and this is the
	 * assertion that would notice if that ever stopped being true. It reads the body as text on
	 * purpose: a field added anywhere in the tree, at any depth, puts its value in here.
	 */
	@Test
	void neverShipsAnIdentifierAPickupAddressOrAnInternalDetailInAnyState() throws Exception {
		String delivery = createDelivery();
		Courier courier = onDutyCourierSharingLocation();
		assertThat(snapshotFor(delivery)).doesNotContain(delivery).doesNotContain(PICKUP_LABEL);

		assign(delivery, courier);
		assertThat(snapshotFor(delivery)).doesNotContain(delivery)
			.doesNotContain(PICKUP_LABEL)
			.doesNotContain(courier.accountId().toString())
			.doesNotContain(DemoAccounts.DISPATCHER_DISPLAY_NAME);

		progress(courier, delivery, "pickup", 1);
		assertThat(snapshotFor(delivery)).doesNotContain(delivery)
			.doesNotContain(PICKUP_LABEL)
			.doesNotContain(courier.accountId().toString())
			.doesNotContain("\"version\"");
	}

	private String snapshotFor(String deliveryId) throws Exception {
		BrowserLikeClient holder = new BrowserLikeClient(this.mockMvc);
		holder.send(get("/track"));
		String url = JsonPath.read(this.dispatcher
			.send(post("/api/deliveries/{id}/tracking-link/copy", deliveryId)).getContentAsString(), "$.url");
		assertThat(holder.send(post("/api/tracking-session").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"%s\"}".formatted(url.substring(url.indexOf("#t=") + 3))))
			.getStatus()).isEqualTo(204);

		MockHttpServletResponse response = holder.send(get("/api/tracking/snapshot"));
		assertThat(response.getStatus()).isEqualTo(200);
		return response.getContentAsString();
	}

	/** A JSON value, or null when the response simply does not carry that path at all. */
	private static Object read(String json, String path) {
		try {
			return JsonPath.read(json, path);
		}
		catch (PathNotFoundException ex) {
			return null;
		}
	}

	private String createDelivery() throws Exception {
		MockHttpServletResponse response = this.dispatcher
			.send(post("/api/deliveries").contentType(MediaType.APPLICATION_JSON).content("""
					{"reference":"DG-RCP-%04d",
					 "pickup":{"addressLabel":"%s","latitude":51.5074,"longitude":-0.1278},
					 "handoff":{"addressLabel":"%s","latitude":51.5090,"longitude":-0.1300}}
					""".formatted(SEQUENCE.incrementAndGet(), PICKUP_LABEL, HANDOFF_LABEL)));
		assertThat(response.getStatus()).isEqualTo(201);
		return JsonPath.read(response.getContentAsString(), "$.id");
	}

	private String referenceOf(String deliveryId) throws Exception {
		return JsonPath.read(this.dispatcher.send(get("/api/deliveries/{id}", deliveryId)).getContentAsString(),
				"$.reference");
	}

	private void cancel(String deliveryId) throws Exception {
		assertThat(this.dispatcher.send(post("/api/deliveries/{id}/cancel", deliveryId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"expectedVersion":0,"reason":"NO_LONGER_REQUIRED","commandId":"%s"}
					""".formatted(UUID.randomUUID())))
			.getStatus()).isEqualTo(200);
	}

	private void assign(String deliveryId, Courier courier) throws Exception {
		assertThat(this.dispatcher.send(post("/api/deliveries/{id}/assignment", deliveryId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"courierId":"%s","expectedVersion":0,"commandId":"%s"}
					""".formatted(courier.accountId(), UUID.randomUUID())))
			.getStatus()).isEqualTo(204);
	}

	private void progress(Courier courier, String deliveryId, String action, int expectedVersion) throws Exception {
		assertThat(courier.client().send(post("/api/couriers/me/deliveries/{id}/{action}", deliveryId, action)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"commandId":"%s","expectedVersion":%d}
					""".formatted(UUID.randomUUID(), expectedVersion)))
			.getStatus()).isEqualTo(204);
	}

	private void report(Courier courier, double latitude, double longitude, double accuracyMetres) throws Exception {
		assertThat(courier.client().send(post("/api/couriers/me/location-reports")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"generation":"%s","reportingSecret":"%s","longitude":%s,"latitude":%s,
					 "accuracyMetres":%s,"recordedAt":"%s"}
					""".formatted(courier.generation(), courier.reportingSecret(), longitude, latitude, accuracyMetres,
					this.clock.instant())))
			.getStatus()).isEqualTo(200);
	}

	/**
	 * A Courier of this test's own, on duty and sharing. A Courier may hold only one active
	 * Assignment, so borrowing the seeded demo account would couple this class to whatever every
	 * other test happened to leave behind.
	 */
	private Courier onDutyCourierSharingLocation() throws Exception {
		int sequence = SEQUENCE.incrementAndGet();
		UUID accountId = UUID.randomUUID();
		String email = "courier-recipient-%d@delivery-glance.example".formatted(sequence);
		String displayName = "Recipient View Courier %d".formatted(sequence);
		this.jdbcClient.sql("""
				INSERT INTO internal_account (id, email, password_hash, display_name, role, enabled)
				SELECT :id, :email, password_hash, :displayName, 'COURIER', true
				FROM internal_account WHERE email = :sourceEmail
				""")
			.param("id", accountId)
			.param("email", email)
			.param("displayName", displayName)
			.param("sourceEmail", DemoAccounts.COURIER_EMAIL)
			.update();

		BrowserLikeClient client = new BrowserLikeClient(this.mockMvc);
		client.signIn(email, DemoAccounts.COURIER_PASSWORD);
		assertThat(client
			.send(put("/api/couriers/me/duty").contentType(MediaType.APPLICATION_JSON).content("{\"onDuty\":true}"))
			.getStatus()).isEqualTo(200);
		String started = client.send(post("/api/couriers/me/location-sharing")).getContentAsString();
		Courier courier = new Courier(accountId, displayName, client, JsonPath.read(started, "$.generation"),
				JsonPath.read(started, "$.reportingSecret"));
		// Reported here rather than in each test because a Courier with no usable position is not
		// Eligible, so this is what it takes to be assignable at all — not a Recipient-view concern.
		report(courier, COURIER_LATITUDE, COURIER_LONGITUDE, COURIER_ACCURACY_METRES);
		return courier;
	}

	private record Courier(UUID accountId, String displayName, BrowserLikeClient client, String generation,
			String reportingSecret) {
	}

}
