package com.deliveryglance.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.deliveryglance.SecurityConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the deny-by-default policy around the one endpoint this Issue introduces: only
 * GET /api/system is public, every other verb or unmapped API path is rejected.
 */
@WebMvcTest(SystemController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void deniesNonGetRequestsToSystemEndpoint() throws Exception {
		mockMvc.perform(post("/api/system")).andExpect(status().isForbidden());
	}

	@Test
	void deniesUnmappedApiPaths() throws Exception {
		mockMvc.perform(get("/api/unknown")).andExpect(status().isForbidden());
	}

	@Test
	void deniesUnmappedActuatorPaths() throws Exception {
		mockMvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
	}

	@Test
	void permitsFrontendShellRoutesByDefault() throws Exception {
		mockMvc.perform(get("/some-client-route"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("/index.html"));
	}

}
