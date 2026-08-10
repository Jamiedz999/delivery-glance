package com.deliveryglance.dispatch;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.deliveryglance.shared.ApiProblemResponses.problem;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = DispatchController.class)
class DispatchExceptionHandler {

	@ExceptionHandler(DispatchException.class)
	ProblemDetail handle(DispatchException exception) {
		return problem(exception.status(), exception.code(), "Dispatch command refused", exception.getMessage());
	}

}
