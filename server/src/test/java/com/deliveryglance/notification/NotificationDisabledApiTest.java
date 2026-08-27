package com.deliveryglance.notification;

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
 * The same application with notification unconfigured — no queue and no callback token — which is a
 * supported Core deployment. It proves the honesty of the "off" state: the opt-in section reports
 * itself unavailable, an attempt to subscribe is refused rather than storing a channel that could
 * never be reached, and the callbacks answer an unauthenticated caller with 401.
 *
 * <p>It sets no properties, so it shares the default integration context every other test uses
 * rather than starting one of its own.
 */
@IntegrationTest
class NotificationDisabledApiTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private MockMvc mockMvc;

	private BrowserLikeClient dispatcher;

	@BeforeEach
	void signInAsDispatcher() throws Exception {
		this.dispatcher = new BrowserLikeClient(this.mockMvc);
		this.dispatcher.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);
	}

	@Test
	void reportsTheOptInSectionUnavailableAndWithoutASubscription() throws Exception {
		BrowserLikeClient holder = grantHolderFor(createDelivery());

		String state = holder.send(get("/api/tracking/notifications")).getContentAsString();

		assertThat((Boolean) JsonPath.read(state, "$.available")).isFalse();
		assertThat((Object) JsonPath.read(state, "$.subscription")).isNull();
	}

	@Test
	void refusesToStoreASubscriptionItCouldNeverSendTo() throws Exception {
		BrowserLikeClient holder = grantHolderFor(createDelivery());

		MockHttpServletResponse response = holder.send(post("/api/tracking/notifications")
			.contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"EMAIL\",\"target\":\"a@example.com\"}"));

		assertThat(response.getStatus()).isEqualTo(503);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("notification-unavailable");
	}

	@Test
	void refusesTheCallbackWhenNoTokenIsConfigured() throws Exception {
		MockHttpServletResponse response = this.mockMvc
			.perform(post("/api/internal/notifications/begin").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer anything")
				.content("{\"transitionId\":\"%s\"}".formatted(UUID.randomUUID())))
			.andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code"))
			.isEqualTo("notification-callback-unauthorized");
	}

	private String createDelivery() throws Exception {
		MockHttpServletResponse response = this.dispatcher
			.send(post("/api/deliveries").contentType(MediaType.APPLICATION_JSON).content("""
					{"reference":"DG-NOTIFOFF-%04d",
					 "pickup":{"addressLabel":"Depot","latitude":51.5074,"longitude":-0.1278},
					 "handoff":{"addressLabel":"Flat 2","latitude":51.5090,"longitude":-0.1300}}
					""".formatted(SEQUENCE.incrementAndGet())));
		assertThat(response.getStatus()).isEqualTo(201);
		return JsonPath.read(response.getContentAsString(), "$.id");
	}

	private BrowserLikeClient grantHolderFor(String deliveryId) throws Exception {
		BrowserLikeClient holder = new BrowserLikeClient(this.mockMvc);
		holder.send(get("/track"));
		String url = JsonPath.read(this.dispatcher
			.send(post("/api/deliveries/{id}/tracking-link/copy", deliveryId)).getContentAsString(), "$.url");
		assertThat(holder.send(post("/api/tracking-session").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"%s\"}".formatted(url.substring(url.indexOf("#t=") + 3)))).getStatus()).isEqualTo(204);
		return holder;
	}

}
