package com.deliveryglance.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.deliveryglance.SecurityConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
@Import(SecurityConfig.class)
class SystemControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsApplicationStatusWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/system"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{"application":"delivery-glance","status":"ok","proofCaptureEnabled":false}
						"""));
	}

}
