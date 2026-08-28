package com.deliveryglance.trackinglink;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.deliveryglance.shared.ApiProblemResponses.problem;

/**
 * The Tracking API's error contract. Its whole job is to make failures look alike.
 *
 * <p>Every holder-facing failure — unknown token, malformed token, expired link, expired grant,
 * missing cookie, unreadable body — becomes the same {@code 404} with the same wording. The status
 * is 404 rather than 410 on purpose: 410 says "this used to exist", which is precisely the fact a
 * guesser wants and RFC 7662 says an introspection response must not reveal.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
// recipientview is named by package rather than by controller class because it depends on this
// module for authorization; referring to its controller here would close the import cycle. It is
// covered at all because the refusal is the same refusal — a Link Holder must not be able to tell
// "the exchange failed" from "the view refused me", and one handler is the only way to be sure.
@RestControllerAdvice(basePackages = { "com.deliveryglance.trackinglink", "com.deliveryglance.recipientview" })
class TrackingExceptionHandler {

	@ExceptionHandler(UnavailableLinkException.class)
	ProblemDetail handleUnavailable() {
		return unavailable();
	}

	/**
	 * An unreadable or absent request body on the exchange. Answering it with the shared 400 would
	 * separate "you sent nonsense" from "that link is unknown", and the two have to be one response.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail handleUnreadableBody() {
		return unavailable();
	}

	@ExceptionHandler(TrackingLinkNotFoundException.class)
	ProblemDetail handleMissingLink(TrackingLinkNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "tracking-link-not-found", "Tracking Link not found",
				exception.getMessage());
	}

	/**
	 * A Dispatcher acting on an already-revoked link — copying it, or revoking it again. Answered
	 * with a plain {@code 409} rather than the generic Unavailable response, because this is the
	 * operator's surface, not the Link Holder's, and telling the Dispatcher what happened is not an
	 * oracle to anyone who could not already read the Delivery.
	 */
	@ExceptionHandler(TrackingLinkRevokedException.class)
	ProblemDetail handleRevoked(TrackingLinkRevokedException exception) {
		return problem(HttpStatus.CONFLICT, "tracking-link-revoked", "Tracking Link revoked",
				exception.getMessage());
	}

	private static ProblemDetail unavailable() {
		return problem(HttpStatus.NOT_FOUND, "tracking-link-unavailable", "Tracking link unavailable",
				"This tracking link is no longer available. Contact the delivery team that shared it.");
	}

}
