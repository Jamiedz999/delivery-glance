package com.deliveryglance.courier;

import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The Courier routes are protected server-side. A Dispatcher is not a lesser Courier: the routes
 * that describe one person's duty and whereabouts are for that person alone.
 */
@IntegrationTest
class CourierRouteAuthorizationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void refusesCourierRoutesWithoutAnInternalAccountSession() throws Exception {
		BrowserLikeClient client = new BrowserLikeClient(this.mockMvc);

		MockHttpServletResponse response = client.send(get("/api/couriers/me"));

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code"))
			.isEqualTo("authentication-required");
	}

	@Test
	void refusesCourierRoutesToASignedInDispatcher() throws Exception {
		BrowserLikeClient client = new BrowserLikeClient(this.mockMvc);
		client.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);

		MockHttpServletResponse response = client.send(get("/api/couriers/me"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("access-denied");
	}

	@Test
	void refusesStartingLocationSharingWithoutTheCsrfHeader() throws Exception {
		BrowserLikeClient client = new BrowserLikeClient(this.mockMvc);
		client.signIn(DemoAccounts.COURIER_EMAIL, DemoAccounts.COURIER_PASSWORD);

		MockHttpServletResponse response = client.sendWithoutCsrfHeader(post("/api/couriers/me/location-sharing"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("csrf-token-invalid");
	}

}
