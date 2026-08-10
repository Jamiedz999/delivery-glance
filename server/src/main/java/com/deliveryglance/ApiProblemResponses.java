package com.deliveryglance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Security failures are produced by filters, outside the handler pipeline that would normally
 * render a {@link org.springframework.http.ProblemDetail}. These writers keep those responses in
 * the same shape as the ones controllers return.
 */
final class ApiProblemResponses {

	static final String ERROR_TYPE_PREFIX = "urn:delivery-glance:error:";

	private ApiProblemResponses() {
	}

	/**
	 * Every argument is a compile-time constant in this application, so the JSON is assembled
	 * directly rather than through a message converter.
	 */
	static void write(HttpServletResponse response, HttpStatus status, String code, String title, String detail)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter()
			.write("""
					{"type":"%s%s","title":"%s","status":%d,"detail":"%s","code":"%s"}"""
				.formatted(ERROR_TYPE_PREFIX, code, title, status.value(), detail, code));
	}

}
