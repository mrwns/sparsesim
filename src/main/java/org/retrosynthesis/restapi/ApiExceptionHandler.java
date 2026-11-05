package org.retrosynthesis.restapi;

import org.retrosynthesis.index.InvalidQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns caller errors into RFC 9457 problem details (400) instead of a generic 500. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidQueryException.class)
    public ProblemDetail invalidQuery(InvalidQueryException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
