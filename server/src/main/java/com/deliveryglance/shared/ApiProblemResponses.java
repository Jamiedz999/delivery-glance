package com.deliveryglance.shared;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * One shape for every API error, so a client can branch on {@code code} without caring which layer
 * refused it. Modules build a {@link ProblemDetail}; the security filters, which run outside the
 * handler pipeline that would render one, write the same JSON directly.
 */
public final class ApiProblemResponses {

	private static final String ERROR_TYPE_PREFIX = "urn:delivery-glance:error:";

	private ApiProblemResponses() {
	}

	public static ProblemDetail problem(HttpStatus status, String code, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create(ERROR_TYPE_PREFIX + code));
		problem.setTitle(title);
		problem.setProperty("code", code);
		return problem;
	}

	/**
	 * Every argument is a compile-time constant in this application, so the JSON is assembled
	 * directly rather than through a message converter.
	 */
	public static void write(HttpServletResponse response, HttpStatus status, String code, String title, String detail)
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
