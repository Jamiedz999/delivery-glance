package com.deliveryglance.delivery;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The Dispatcher slice of the Delivery lifecycle, driven end to end: create, list, reopen and
 * cancel while a Delivery is still Awaiting Courier.
 */
@IntegrationTest
class DeliveryApiTest {

	private static final AtomicInteger REFERENCE_SEQUENCE = new AtomicInteger();

	@Autowired
	private MockMvc mockMvc;

	private BrowserLikeClient client;

	@BeforeEach
	void signInAsDispatcher() throws Exception {
		this.client = new BrowserLikeClient(this.mockMvc);
		this.client.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);
	}

	@Test
	void createsADeliveryAwaitingACourierWithItsFirstTransition() throws Exception {
		String reference = nextReference();

		MockHttpServletResponse response = create(reference);

		assertThat(response.getStatus()).isEqualTo(201);
		String body = response.getContentAsString();
		assertThat(response.getHeader("Location")).isEqualTo("/api/deliveries/" + JsonPath.read(body, "$.id"));
		assertThat((String) JsonPath.read(body, "$.reference")).isEqualTo(reference);
		assertThat((String) JsonPath.read(body, "$.state")).isEqualTo("AWAITING_COURIER");
		assertThat((Integer) JsonPath.read(body, "$.version")).isZero();
		assertThat((String) JsonPath.read(body, "$.pickup.addressLabel")).isEqualTo("Warehouse 4, Riverside Estate");
		assertThat((Double) JsonPath.read(body, "$.handoff.latitude")).isEqualTo(51.5033);
		assertThat(JsonPath.<java.util.List<Object>>read(body, "$.transitions")).hasSize(1);
		assertThat((String) JsonPath.read(body, "$.transitions[0].nextState")).isEqualTo("AWAITING_COURIER");
		assertThat(JsonPath.<Object>read(body, "$.transitions[0].previousState")).isNull();
		assertThat((String) JsonPath.read(body, "$.transitions[0].actorDisplayName"))
			.isEqualTo(DemoAccounts.DISPATCHER_DISPLAY_NAME);
	}

	@Test
	void listsCreatedDeliveriesAndReopensOneById() throws Exception {
		String reference = nextReference();
		String id = idOf(create(reference));

		MockHttpServletResponse list = this.client.send(get("/api/deliveries"));
		assertThat(list.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<java.util.List<String>>read(list.getContentAsString(), "$[*].reference"))
			.contains(reference);

		MockHttpServletResponse detail = this.client.send(get("/api/deliveries/{id}", id));
		assertThat(detail.getStatus()).isEqualTo(200);
		assertThat((String) JsonPath.read(detail.getContentAsString(), "$.reference")).isEqualTo(reference);
	}

	@Test
	void answersAnUnknownDeliveryWithNotFound() throws Exception {
		MockHttpServletResponse response = this.client.send(get("/api/deliveries/{id}", UUID.randomUUID()));

		assertThat(response.getStatus()).isEqualTo(404);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("delivery-not-found");
	}

	@Test
	void rejectsADeliveryReferenceThatIsAlreadyInUse() throws Exception {
		String reference = nextReference();
		create(reference);

		MockHttpServletResponse response = create(reference);

		assertThat(response.getStatus()).isEqualTo(409);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code"))
			.isEqualTo("delivery-reference-taken");
	}

	@Test
	void rejectsCoordinatesOutsideTheWgs84Range() throws Exception {
		MockHttpServletResponse response = this.client.send(post("/api/deliveries")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"reference":"%s",
					 "pickup":{"addressLabel":"Warehouse 4","latitude":91.0,"longitude":-0.1},
					 "handoff":{"addressLabel":"Flat 2","latitude":51.5,"longitude":-181.0}}
					""".formatted(nextReference())));

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentAsString()).contains("pickup.latitude").contains("handoff.longitude");
	}

	@Test
	void rejectsADeliveryWithoutTheRequiredFields() throws Exception {
		MockHttpServletResponse response = this.client.send(post("/api/deliveries")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"reference":"  ","pickup":null,"handoff":null}
					"""));

		assertThat(response.getStatus()).isEqualTo(400);
	}

	@Test
	void cancelsADeliveryThatIsStillAwaitingACourier() throws Exception {
		String id = idOf(create(nextReference()));

		MockHttpServletResponse response = cancel(id, UUID.randomUUID(), 0, "NO_LONGER_REQUIRED", null);

		assertThat(response.getStatus()).isEqualTo(200);
		String body = response.getContentAsString();
		assertThat((String) JsonPath.read(body, "$.state")).isEqualTo("CANCELLED");
		assertThat((Integer) JsonPath.read(body, "$.version")).isEqualTo(1);
		assertThat(JsonPath.<java.util.List<Object>>read(body, "$.transitions")).hasSize(2);
		assertThat((String) JsonPath.read(body, "$.transitions[1].previousState")).isEqualTo("AWAITING_COURIER");
		assertThat((String) JsonPath.read(body, "$.transitions[1].reasonCode")).isEqualTo("NO_LONGER_REQUIRED");
	}

	@Test
	void rejectsACancelThatExpectedAnOlderVersion() throws Exception {
		String id = idOf(create(nextReference()));
		cancel(id, UUID.randomUUID(), 0, "NO_LONGER_REQUIRED", null);

		MockHttpServletResponse response = cancel(id, UUID.randomUUID(), 0, "NO_LONGER_REQUIRED", null);

		assertThat(response.getStatus()).isEqualTo(409);
		String body = response.getContentAsString();
		assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("delivery-version-conflict");
		assertThat((Integer) JsonPath.read(body, "$.currentVersion")).isEqualTo(1);
		assertThat((String) JsonPath.read(body, "$.currentState")).isEqualTo("CANCELLED");
	}

	@Test
	void treatsARetriedCancelCommandAsTheSameCommand() throws Exception {
		String id = idOf(create(nextReference()));
		UUID commandId = UUID.randomUUID();
		cancel(id, commandId, 0, "ITEM_UNAVAILABLE_AT_PICKUP", null);

		MockHttpServletResponse retry = cancel(id, commandId, 0, "ITEM_UNAVAILABLE_AT_PICKUP", null);

		assertThat(retry.getStatus()).isEqualTo(200);
		String body = retry.getContentAsString();
		assertThat((String) JsonPath.read(body, "$.state")).isEqualTo("CANCELLED");
		assertThat((Integer) JsonPath.read(body, "$.version")).isEqualTo(1);
		assertThat(JsonPath.<java.util.List<Object>>read(body, "$.transitions")).hasSize(2);
	}

	@Test
	void rejectsANewCancelCommandOnAnAlreadyCancelledDelivery() throws Exception {
		String id = idOf(create(nextReference()));
		cancel(id, UUID.randomUUID(), 0, "NO_LONGER_REQUIRED", null);

		MockHttpServletResponse response = cancel(id, UUID.randomUUID(), 1, "NO_LONGER_REQUIRED", null);

		assertThat(response.getStatus()).isEqualTo(409);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code"))
			.isEqualTo("delivery-invalid-transition");
	}

	@Test
	void requiresANoteWhenTheCancellationReasonIsOther() throws Exception {
		String id = idOf(create(nextReference()));

		MockHttpServletResponse response = cancel(id, UUID.randomUUID(), 0, "OTHER", null);

		assertThat(response.getStatus()).isEqualTo(400);
	}

	@Test
	void acceptsOtherWithANote() throws Exception {
		String id = idOf(create(nextReference()));

		MockHttpServletResponse response = cancel(id, UUID.randomUUID(), 0, "OTHER", "Depot closed for the day");

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.transitions[1].reasonNote"))
			.isEqualTo("Depot closed for the day");
	}

	@Test
	void cancellingAnUnknownDeliveryIsNotFound() throws Exception {
		MockHttpServletResponse response = cancel(UUID.randomUUID().toString(), UUID.randomUUID(), 0,
				"NO_LONGER_REQUIRED", null);

		assertThat(response.getStatus()).isEqualTo(404);
	}

	private MockHttpServletResponse create(String reference) throws Exception {
		return this.client.send(post("/api/deliveries").contentType(MediaType.APPLICATION_JSON).content("""
				{"reference":"%s",
				 "pickup":{"addressLabel":"Warehouse 4, Riverside Estate","latitude":51.5074,"longitude":-0.1278},
				 "handoff":{"addressLabel":"Flat 2, 14 Elm Row","latitude":51.5033,"longitude":-0.1195}}
				""".formatted(reference)));
	}

	private MockHttpServletResponse cancel(String id, UUID commandId, int expectedVersion, String reason, String note)
			throws Exception {
		String noteJson = (note == null) ? "null" : "\"" + note + "\"";
		return this.client
			.send(post("/api/deliveries/{id}/cancel", id).contentType(MediaType.APPLICATION_JSON).content("""
					{"commandId":"%s","expectedVersion":%d,"reason":"%s","note":%s}
					""".formatted(commandId, expectedVersion, reason, noteJson)));
	}

	private static String idOf(MockHttpServletResponse response) throws Exception {
		return JsonPath.read(response.getContentAsString(), "$.id");
	}

	private static String nextReference() {
		return "DG-%04d".formatted(REFERENCE_SEQUENCE.incrementAndGet());
	}

}
