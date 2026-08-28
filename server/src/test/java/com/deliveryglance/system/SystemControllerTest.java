package com.deliveryglance.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.deliveryglance.SecurityConfig;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
@Import(SecurityConfig.class)
class SystemControllerTest {

	@Autowired
	private MockMvc mockMvc;

	// The probe reads the internal_account rows, which this slice does not stand up. Its own two
	// sides are proved against a real database in DemoAccountsProbeTest; here it is stubbed so the
	// endpoint's shape can be asserted for each answer it might return.
	@MockitoBean
	private DemoAccountsProbe demoAccountsProbe;

	@Test
	void returnsApplicationStatusWithoutAuthentication() throws Exception {
		when(this.demoAccountsProbe.demoAccountsUnchanged()).thenReturn(true);

		this.mockMvc.perform(get("/api/system"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"application":"delivery-glance","status":"ok","proofCaptureEnabled":false,"etaEnabled":false,"demoAccountsUnchanged":true}
					"""));
	}

	@Test
	void reportsDemoAccountsChangedWhenTheProbeSaysSo() throws Exception {
		when(this.demoAccountsProbe.demoAccountsUnchanged()).thenReturn(false);

		this.mockMvc.perform(get("/api/system"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"demoAccountsUnchanged":false}
					"""));
	}

}
