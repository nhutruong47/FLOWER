package com.flowerchain.shared.web;

import com.flowerchain.shared.api.FlowerChainException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    private String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        return (header != null && !header.isBlank()) ? header : "req_" + UUID.randomUUID();
    }
}
