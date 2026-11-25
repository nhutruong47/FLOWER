package com.flowerchain.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.OptimisticLockException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

/** Security review F1: a lost optimistic-lock race is a clean 409 Problem Details, never a 500. */
class GlobalExceptionHandlerConcurrencyTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(Clock.fixed(Instant.parse("2026-11-25T08:00:00Z"), ZoneOffset.UTC));

    static Stream<RuntimeException> concurrencyFailures() {
        return Stream.of(
                new ObjectOptimisticLockingFailureException("OrderEntity", "some-id"),
                new OptimisticLockException("stale version"),
                new CannotAcquireLockException("deadlock detected"));
    }

    @ParameterizedTest
    @MethodSource("concurrencyFailures")
    void concurrencyFailureBecomes409ConcurrentModification(RuntimeException failure) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/storefront/orders/x/cancel");

        ResponseEntity<ProblemDetail> response = handler.handleConcurrentModification(failure, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties()).containsEntry("errorCode", "CONCURRENT_MODIFICATION");
        assertThat(problem.getDetail()).doesNotContain("some-id", "stale version", "deadlock");
    }

    @ParameterizedTest
    @MethodSource("concurrencyFailures")
    void springRoutesConcurrencyFailuresToThe409HandlerNotTheCatchAll(RuntimeException failure) {
        Method resolved = new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class)
                .resolveMethodByThrowable(failure);

        assertThat(resolved).isNotNull();
        assertThat(resolved.getName()).isEqualTo("handleConcurrentModification");
    }
}
