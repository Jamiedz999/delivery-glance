package com.deliveryglance.delivery;

import java.util.UUID;

import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Dispatcher Delivery routes are protected server-side, so hiding them in the UI is never the only
 * thing keeping a Courier out.
 */
@IntegrationTest
class DeliveryRouteAuthorizationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void refusesDeliveryRoutesWithoutAnInternalAccountSession() throws Exception {
		BrowserLikeClient client = new BrowserLikeClient(this.mockMvc);

		MockHttpServletResponse response = client.send(get("/api/deliveries"));

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code"))
			.isEqualTo("authentication-required");
	}

	@Test
	void refusesDeliveryReadsToASignedInCourier() throws Exception {
		BrowserLikeClient client = signedInCourier();

		MockHttpServletResponse response = client.send(get("/api/deliveries"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("access-denied");
	}

	@Test
	void refusesDeliveryCreationToASignedInCourier() throws Exception {
		BrowserLikeClient client = signedInCourier();

		MockHttpServletResponse response = client.send(createRequest("DG-COURIER-1"));

		assertThat(response.getStatus()).isEqualTo(403);
	}

	@Test
	void refusesCourierRecommendationAndAssignmentToASignedInCourier() throws Exception {
		BrowserLikeClient client = signedInCourier();
		UUID deliveryId = UUID.randomUUID();

		assertThat(client.send(get("/api/deliveries/{id}/courier-recommendations", deliveryId)).getStatus())
			.isEqualTo(403);
		assertThat(client.send(post("/api/deliveries/{id}/assignment", deliveryId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"courierId":"%s","expectedVersion":0,"commandId":"%s"}
					""".formatted(UUID.randomUUID(), UUID.randomUUID()))).getStatus()).isEqualTo(403);
	}

	@Test
	void refusesDeliveryCreationWithoutTheCsrfHeader() throws Exception {
		BrowserLikeClient client = signedInDispatcher();

		MockHttpServletResponse response = client.sendWithoutCsrfHeader(createRequest("DG-NO-CSRF-1"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("csrf-token-invalid");
	}

	@Test
	void allowsDeliveryCreationToASignedInDispatcher() throws Exception {
		BrowserLikeClient client = signedInDispatcher();

		MockHttpServletResponse response = client.send(createRequest("DG-AUTHORIZED-1"));

		assertThat(response.getStatus()).isEqualTo(201);
	}

	private BrowserLikeClient signedInDispatcher() throws Exception {
		BrowserLikeClient client = new BrowserLikeClient(this.mockMvc);
		client.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);
		return client;
	}

	private BrowserLikeClient signedInCourier() throws Exception {
		BrowserLikeClient client = new BrowserLikeClient(this.mockMvc);
		client.signIn(DemoAccounts.COURIER_EMAIL, DemoAccounts.COURIER_PASSWORD);
		return client;
	}

	private static MockHttpServletRequestBuilder createRequest(String reference) {
		return post("/api/deliveries").contentType(MediaType.APPLICATION_JSON).content("""
				{"reference":"%s",
				 "pickup":{"addressLabel":"Warehouse 4, Riverside Estate","latitude":51.5074,"longitude":-0.1278},
				 "handoff":{"addressLabel":"Flat 2, 14 Elm Row","latitude":51.5033,"longitude":-0.1195}}
				""".formatted(reference));
	}

}
