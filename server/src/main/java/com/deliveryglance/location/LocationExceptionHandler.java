package com.deliveryglance.location;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.deliveryglance.shared.ApiProblemResponses.problem;

/**
 * The Location Sharing API's own refusal. It is a conflict rather than an authorization failure:
 * the Courier is signed in and allowed here, but the page they are reporting from no longer holds a
 * live Location Sharing Session.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = LocationSharingController.class)
class LocationExceptionHandler {

	@ExceptionHandler(LocationSharingEndedException.class)
	ProblemDetail handleSharingEnded(LocationSharingEndedException exception) {
		return problem(HttpStatus.CONFLICT, "location-sharing-ended", "Location Sharing has ended",
				exception.getMessage());
	}

}
