package com.deliveryglance.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.SecurityConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the deny-by-default policy around the public endpoints: only GET /api/system is open, an
 * unsafe request needs its CSRF token, and anything else needs an Internal Account session.
 */
@WebMvcTest(SystemController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void deniesNonGetRequestsToSystemEndpoint() throws Exception {
		BrowserLikeClient client = new BrowserLikeClient(mockMvc);
		client.send(get("/api/system"));

		assertThat(client.send(post("/api/system")).getStatus()).isEqualTo(401);
	}

	@Test
	void deniesUnsafeRequestsWithoutACsrfToken() throws Exception {
		mockMvc.perform(post("/api/system"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("csrf-token-invalid"));
	}

	@Test
	void deniesUnmappedApiPaths() throws Exception {
		mockMvc.perform(get("/api/unknown"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("authentication-required"));
	}

	@Test
	void deniesUnmappedActuatorPaths() throws Exception {
		mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
	}

	@Test
	void permitsFrontendShellRoutesByDefault() throws Exception {
		mockMvc.perform(get("/some-client-route"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("/index.html"));
	}

}
