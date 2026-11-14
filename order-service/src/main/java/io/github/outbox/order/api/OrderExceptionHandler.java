package io.github.outbox.order.api;

import io.github.outbox.order.api.OrderProblems.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Clock;
import java.util.List;

/**
 * Answers every failure of the service as a problem detail (RFC 9457).
 * <p>
 * Not scoped to the controllers' package: a request no handler matches (an unknown path, an unsupported method or
 * content type) fails before a controller is chosen, and a scoped advice would leave it to the default error page.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class OrderExceptionHandler extends ResponseEntityExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception exception, HttpServletRequest request) {
        log.error("Request {} failed", request.getRequestURI(), exception);
        ProblemDetail problem = OrderProblems.internalError(request.getRequestURI(), clock.instant());
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldViolation> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return validationFailed(exception, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<FieldViolation> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                error instanceof FieldError fieldError
                                        ? fieldError.getField()
                                        : result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage()
                        )))
                .toList();
        return validationFailed(exception, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
                                                             HttpHeaders headers, HttpStatusCode statusCode,
                                                             WebRequest request) {
        String uri = ((ServletWebRequest) request).getRequest().getRequestURI();
        if (statusCode.is5xxServerError()) {
            log.error("Request {} failed", uri, exception);
        } else {
            log.debug("Request {} answered {}: {}", uri, statusCode.value(), exception.getClass().getSimpleName());
        }
        // Spring builds the body of most standard exceptions inside super, so it is stamped on the way out.
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            OrderProblems.stamp(problem, uri, clock.instant());
        }
        return response;
    }

    private ResponseEntity<Object> validationFailed(Exception exception, List<FieldViolation> errors,
                                                    HttpHeaders headers, WebRequest request) {
        String uri = ((ServletWebRequest) request).getRequest().getRequestURI();
        ProblemDetail problem = OrderProblems.validationFailed(errors, uri, clock.instant());
        return handleExceptionInternal(exception, problem, headers, HttpStatusCode.valueOf(problem.getStatus()),
                request);
    }
}
