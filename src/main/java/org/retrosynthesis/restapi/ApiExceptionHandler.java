package org.retrosynthesis.restapi;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.retrosynthesis.index.InvalidQueryException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Turns caller errors into RFC 9457 problem details (400) instead of a generic 500. Extending
 * {@link ResponseEntityExceptionHandler} also renders Spring MVC's own errors (malformed JSON, 405, ...) as problem
 * details.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidQueryException.class)
    public ProblemDetail invalidQuery(InvalidQueryException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        if (ex.getCause() instanceof UnrecognizedPropertyException e) {
            ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, unknownFieldMessage(e));
            return handleExceptionInternal(ex, body, headers, status, request);
        }
        return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }

    private static String unknownFieldMessage(UnrecognizedPropertyException e) {
        String field = e.getPath().stream()
                .map(JsonMappingException.Reference::getFieldName)
                .filter(name -> name != null)
                .collect(Collectors.joining("."));
        String allowed = e.getKnownPropertyIds().stream()
                .map(String::valueOf)
                .sorted()
                .collect(Collectors.joining(", "));
        return "Unknown field '" + field + "'; allowed fields: " + allowed;
    }
}
