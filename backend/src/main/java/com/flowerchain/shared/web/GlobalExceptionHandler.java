package com.flowerchain.shared.web;

import com.flowerchain.shared.api.FlowerChainException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(FlowerChainException.class)
    public ResponseEntity<ProblemDetail> handleDomainException(
            FlowerChainException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setType(URI.create("https://flowerchain.com/problems/" + ex.getErrorCode().toLowerCase().replace('_', '-')));
        problem.setTitle(ex.getTitle());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("requestId", resolveRequestId(request));
        problem.setProperty("timestamp", Instant.now(clock).toString());
        return ResponseEntity.status(ex.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "One or more request fields failed validation.");
        problem.setType(URI.create("https://flowerchain.com/problems/validation-failed"));
        problem.setTitle("Validation failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", "VALIDATION_FAILED");
        problem.setProperty("requestId", resolveRequestId(request));
        problem.setProperty("timestamp", Instant.now(clock).toString());
        problem.setProperty("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /**
     * A concurrent change won (optimistic version mismatch, or a lock conflict such as a deadlock victim). Nothing
     * from this request was committed; the client may reload and retry.
     */
    @ExceptionHandler({ConcurrencyFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ProblemDetail> handleConcurrentModification(
            RuntimeException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        log.warn("Concurrent modification for requestId={} uri={}: {}",
                requestId, request.getRequestURI(), ex.getClass().getSimpleName());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The resource was changed by another request at the same time. Reload it and try again.");
        problem.setType(URI.create("https://flowerchain.com/problems/concurrent-modification"));
        problem.setTitle("Concurrent modification");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", "CONCURRENT_MODIFICATION");
        problem.setProperty("requestId", requestId);
        problem.setProperty("timestamp", Instant.now(clock).toString());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /** No endpoint for this method and path: 404 Problem Details instead of the 500 catch-all. */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ProblemDetail> handleNoEndpoint(Exception ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No such endpoint.");
        problem.setType(URI.create("https://flowerchain.com/problems/resource-not-found"));
        problem.setTitle("Resource not found");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", "RESOURCE_NOT_FOUND");
        problem.setProperty("requestId", resolveRequestId(request));
        problem.setProperty("timestamp", Instant.now(clock).toString());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        String errorCode = "Idempotency-Key".equalsIgnoreCase(ex.getHeaderName())
                ? "IDEMPOTENCY_KEY_REQUIRED"
                : "MISSING_REQUIRED_HEADER";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Required header '" + ex.getHeaderName() + "' is missing.");
        problem.setType(URI.create("https://flowerchain.com/problems/" + errorCode.toLowerCase().replace('_', '-')));
        problem.setTitle("Missing required header");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("requestId", resolveRequestId(request));
        problem.setProperty("timestamp", Instant.now(clock).toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return badRequest(
                "MISSING_REQUIRED_PARAMETER",
                "Missing required parameter",
                "Required query parameter '" + ex.getParameterName() + "' is missing.",
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        // The rejected value is not echoed back: it is client input and may be arbitrarily long.
        return badRequest(
                "INVALID_PARAMETER_FORMAT",
                "Invalid parameter format",
                "Parameter '" + ex.getName() + "' has an invalid format.",
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return badRequest(
                "MALFORMED_REQUEST_BODY",
                "Malformed request body",
                "The request body is missing or is not valid JSON for this endpoint.",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(
            Exception ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        log.error("Unhandled exception for requestId={} uri={}", requestId, request.getRequestURI(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred. Reference requestId for support.");
        problem.setType(URI.create("https://flowerchain.com/problems/internal-error"));
        problem.setTitle("Internal server error");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", "INTERNAL_ERROR");
        problem.setProperty("requestId", requestId);
        problem.setProperty("timestamp", Instant.now(clock).toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private ResponseEntity<ProblemDetail> badRequest(
            String errorCode, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(URI.create("https://flowerchain.com/problems/" + errorCode.toLowerCase().replace('_', '-')));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("requestId", resolveRequestId(request));
        problem.setProperty("timestamp", Instant.now(clock).toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        return (header != null && !header.isBlank()) ? header : "req_" + UUID.randomUUID();
    }
}
