package com.deliveryglance.identityaccess;

import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.IntegrationTest;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@IntegrationTest
class SessionApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private BrowserLikeClient client;

	@BeforeEach
	void setUp() {
		this.client = new BrowserLikeClient(this.mockMvc);
	}

	@Test
	void signsInAPreProvisionedDispatcherAndReportsDisplayNameAndRole() throws Exception {
		MockHttpServletResponse signIn = this.client.signIn(DemoAccounts.DISPATCHER_EMAIL,
				DemoAccounts.DISPATCHER_PASSWORD);
		assertThat(signIn.getStatus()).isEqualTo(204);

		MockHttpServletResponse session = this.client.send(get("/api/session"));

		assertThat(session.getStatus()).isEqualTo(200);
		assertJsonEquals(session.getContentAsString(), """
				{"displayName":"%s","role":"DISPATCHER"}
				""".formatted(DemoAccounts.DISPATCHER_DISPLAY_NAME));
	}

	@Test
	void signsInAPreProvisionedCourier() throws Exception {
		this.client.signIn(DemoAccounts.COURIER_EMAIL, DemoAccounts.COURIER_PASSWORD);

		MockHttpServletResponse session = this.client.send(get("/api/session"));

		assertJsonEquals(session.getContentAsString(), """
				{"displayName":"%s","role":"COURIER"}
				""".formatted(DemoAccounts.COURIER_DISPLAY_NAME));
	}

	@Test
	void acceptsAnEmailThatIsNotNormalised() throws Exception {
		MockHttpServletResponse signIn = this.client.signIn("  DISPATCHER@Delivery-Glance.Example  ",
				DemoAccounts.DISPATCHER_PASSWORD);

		assertThat(signIn.getStatus()).isEqualTo(204);
	}

	@Test
	void answersAWrongPasswordAndAnUnknownEmailIdentically() throws Exception {
		MockHttpServletResponse wrongPassword = this.client.signIn(DemoAccounts.DISPATCHER_EMAIL, "not-the-password");
		MockHttpServletResponse unknownEmail = new BrowserLikeClient(this.mockMvc)
			.signIn("nobody@delivery-glance.example", DemoAccounts.DISPATCHER_PASSWORD);

		assertThat(wrongPassword.getStatus()).isEqualTo(401);
		assertThat(unknownEmail.getStatus()).isEqualTo(401);
		assertThat(wrongPassword.getContentAsString()).isEqualTo(unknownEmail.getContentAsString());
		assertJsonEquals(wrongPassword.getContentAsString(), """
				{"type":"urn:delivery-glance:error:invalid-credentials","title":"Sign-in failed","status":401,
				 "detail":"The email and password do not match an enabled Internal Account.",
				 "code":"invalid-credentials"}
				""");
	}

	@Test
	void refusesADisabledInternalAccountWithTheSameFailure() throws Exception {
		String email = "retired-dispatcher@delivery-glance.example";
		this.jdbcClient.sql("""
				INSERT INTO internal_account (email, password_hash, display_name, role, enabled)
				VALUES (:email, :passwordHash, 'Robin the Retired', 'DISPATCHER', FALSE)
				ON CONFLICT (email) DO NOTHING
				""")
			.param("email", email)
			.param("passwordHash", this.passwordEncoder.encode(DemoAccounts.DISPATCHER_PASSWORD))
			.update();

		MockHttpServletResponse disabled = this.client.signIn(email, DemoAccounts.DISPATCHER_PASSWORD);
		MockHttpServletResponse wrongPassword = new BrowserLikeClient(this.mockMvc)
			.signIn(DemoAccounts.DISPATCHER_EMAIL, "not-the-password");

		assertThat(disabled.getStatus()).isEqualTo(401);
		assertThat(disabled.getContentAsString()).isEqualTo(wrongPassword.getContentAsString());
		assertThat(this.client.send(get("/api/session")).getStatus()).isEqualTo(401);
	}

	@Test
	void leavesNoSessionBehindAfterAFailedSignIn() throws Exception {
		this.client.signIn(DemoAccounts.DISPATCHER_EMAIL, "not-the-password");

		assertThat(this.client.send(get("/api/session")).getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsASignInWithoutTheCsrfHeader() throws Exception {
		MockHttpServletResponse response = this.client.sendWithoutCsrfHeader(post("/api/session/login")
			.param("email", DemoAccounts.DISPATCHER_EMAIL)
			.param("password", DemoAccounts.DISPATCHER_PASSWORD));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(this.client.send(get("/api/session")).getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsTheSessionQueryWithoutASession() throws Exception {
		MockHttpServletResponse response = this.client.send(get("/api/session"));

		assertThat(response.getStatus()).isEqualTo(401);
		assertJsonEquals(response.getContentAsString(), """
				{"type":"urn:delivery-glance:error:authentication-required","title":"Authentication required",
				 "status":401,"detail":"Sign in with an Internal Account to use this endpoint.",
				 "code":"authentication-required"}
				""");
	}

	@Test
	void keepsTheSessionInTheDatabaseRatherThanInMemory() throws Exception {
		long before = storedSessionsFor(DemoAccounts.DISPATCHER_EMAIL);

		this.client.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);

		assertThat(storedSessionsFor(DemoAccounts.DISPATCHER_EMAIL)).isEqualTo(before + 1);
	}

	private long storedSessionsFor(String email) {
		return this.jdbcClient.sql("SELECT count(*) FROM spring_session WHERE principal_name = :email")
			.param("email", email)
			.query(Long.class)
			.single();
	}

	@Test
	void signsOutSoTheSessionCanNoLongerBeUsed() throws Exception {
		this.client.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);

		assertThat(this.client.signOut().getStatus()).isEqualTo(204);
		assertThat(this.client.send(get("/api/session")).getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsASignOutWithoutTheCsrfHeader() throws Exception {
		this.client.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);

		MockHttpServletResponse response = this.client.sendWithoutCsrfHeader(delete("/api/session"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(this.client.send(get("/api/session")).getStatus()).isEqualTo(200);
	}

	private static void assertJsonEquals(String actual, String expected) throws JSONException {
		JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
	}

}
