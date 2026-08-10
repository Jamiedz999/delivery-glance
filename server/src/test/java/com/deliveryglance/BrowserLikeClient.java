package com.deliveryglance;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Drives the API the way the React client does: it keeps the cookies the server sets, sends them
 * back on later requests, and echoes the CSRF cookie as a request header. Using the real cookies
 * means these tests exercise the session store and CSRF pair rather than a test shortcut around
 * them.
 */
public final class BrowserLikeClient {

	/** Matches {@code CookieCsrfTokenRepository}'s defaults. */
	private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";

	private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

	private final MockMvc mockMvc;

	private final Map<String, String> cookies = new LinkedHashMap<>();

	public BrowserLikeClient(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	public MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
		String csrfToken = this.cookies.get(CSRF_COOKIE_NAME);
		if (csrfToken != null) {
			request.header(CSRF_HEADER_NAME, csrfToken);
		}
		return sendWithoutCsrfHeader(request);
	}

	public MockHttpServletResponse sendWithoutCsrfHeader(MockHttpServletRequestBuilder request) throws Exception {
		this.cookies.forEach((name, value) -> request.cookie(new Cookie(name, value)));
		MvcResult result = this.mockMvc.perform(request).andReturn();
		rememberCookies(result.getResponse());
		return result.getResponse();
	}

	/**
	 * Signs in and leaves the client holding the resulting session, ready for authenticated calls.
	 */
	public MockHttpServletResponse signIn(String email, String password) throws Exception {
		// A first safe request makes the server issue the CSRF cookie the sign-in form needs.
		send(get("/api/system"));
		return send(post("/api/session/login").param("email", email).param("password", password));
	}

	public MockHttpServletResponse signOut() throws Exception {
		return send(delete("/api/session"));
	}

	private void rememberCookies(MockHttpServletResponse response) {
		for (String setCookie : response.getHeaders("Set-Cookie")) {
			int separator = setCookie.indexOf('=');
			int end = setCookie.indexOf(';');
			if (separator < 0) {
				continue;
			}
			String name = setCookie.substring(0, separator);
			String value = setCookie.substring(separator + 1, end < 0 ? setCookie.length() : end);
			if (value.isEmpty() || setCookie.contains("Max-Age=0")) {
				this.cookies.remove(name);
			}
			else {
				this.cookies.put(name, value);
			}
		}
	}

}
