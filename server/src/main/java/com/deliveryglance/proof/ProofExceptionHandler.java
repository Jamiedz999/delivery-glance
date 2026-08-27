package com.deliveryglance.proof;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.deliveryglance.shared.ApiProblemResponses.problem;

/**
 * The proof API's error contract. It is a global advice rather than one scoped to the proof
 * controllers, because a {@link ProofException} is also raised while a handoff command attaches its
 * proof keys — inside the Delivery controller's request — and the same stable {@code code} has to
 * come back wherever it was refused. It declares only {@link ProofException}, so it never competes
 * with another module's advice for that module's own exceptions.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class ProofExceptionHandler {

	@ExceptionHandler(ProofException.class)
	ProblemDetail handle(ProofException exception) {
		return problem(exception.status(), exception.code(), "Proof of delivery refused", exception.getMessage());
	}

}
