package com.deliveryglance.trackinglink;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Makes the browser revalidate the two Recipient application files on every visit.
 *
 * <p>Every other build artefact this application serves carries a content hash in its name, so a
 * browser may keep it forever and a deployment simply asks for different names. These two cannot:
 * the bootstrap script is inlined into /track and pinned by a CSP hash, so it is not regenerated per
 * build and cannot know a hash to ask for. Fixed names and ordinary heuristic caching would mean a
 * returning Recipient running last week's application against this week's API — silently, and for
 * as long as their browser felt like it.
 *
 * <p>{@code no-cache} rather than {@code no-store}: the file may still be stored and is still
 * answered with a 304 when it has not changed, which is what keeps a revalidation cheap. Only the
 * decision to reuse it without asking is taken away. The hashed chunks these two pull in are
 * untouched, and they are almost all of the bytes.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RecipientApplicationHeadersFilter extends OncePerRequestFilter {

	/** The paths the bootstrap names. It is the only thing that requests them. */
	static final Set<String> PATHS = Set.of("/track-app.js", "/track-app.css");

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !PATHS.contains(request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
		filterChain.doFilter(request, response);
	}

}
