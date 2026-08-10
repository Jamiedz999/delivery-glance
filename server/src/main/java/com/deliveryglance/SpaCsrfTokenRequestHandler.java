package com.deliveryglance;

import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * The single-page-application CSRF arrangement from the Spring Security reference: the cookie
 * carries a BREACH-protected token, and a request header carries the raw value the browser read
 * back out of that cookie.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

	private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();

	private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
		this.xor.handle(request, response, csrfToken);
	}

	@Override
	public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
		// A header value came straight from the cookie and is already the raw token; a form
		// parameter was rendered by the server and is masked.
		boolean fromHeader = StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()));
		return (fromHeader ? this.plain : this.xor).resolveCsrfTokenValue(request, csrfToken);
	}

}
