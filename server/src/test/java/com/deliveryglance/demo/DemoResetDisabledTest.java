package com.deliveryglance.demo;

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
 * The default deployment, which is every deployment that has not said otherwise: the reset is not
 * merely unmapped but refused by the security policy.
 *
 * <p>The difference matters. Unmapped would leave {@code POST /api/demo/reset} falling through to
 * the frontend catch-all, which answers any path with the React shell — so a route nobody
 * authorised would have answered {@code 200} with a page. Refusing it in the policy is also what
 * makes "off" a property of the deployment rather than of which beans happen to have been built.
 */
@IntegrationTest
class DemoResetDisabledTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void refusesTheResetForTheDispatcherWhoWouldOtherwiseBeAllowedIt() throws Exception {
		BrowserLikeClient dispatcher = new BrowserLikeClient(this.mockMvc);
		dispatcher.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);

		MockHttpServletResponse response = dispatcher.send(post("/api/demo/reset"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("access-denied");
	}

	@Test
	void refusesTheResetForAnAnonymousCaller() throws Exception {
		BrowserLikeClient anonymous = new BrowserLikeClient(this.mockMvc);
		// A safe request first, so the server issues the CSRF cookie. Without it the refusal would be
		// about the missing token and would say nothing about who is allowed to reset a demo.
		anonymous.send(get("/api/system"));

		assertThat(anonymous.send(post("/api/demo/reset")).getStatus()).isEqualTo(401);
	}

}
