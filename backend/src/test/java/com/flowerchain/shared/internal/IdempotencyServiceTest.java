package com.flowerchain.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowerchain.shared.api.FlowerChainException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

/** Security review F4: key validation, claim-before-action, and same-key races replayed instead of a 500. */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");
    private static final String SCOPE = "STAFF:00000000-0000-0000-0000-00000000e001";
    private static final String ENDPOINT = "POST /api/v1/admin/payments/{paymentId}/refunds";
    private static final String PAYLOAD = "payment|6:500000|reason";

    record Receipt(String id, String amount) {}

    @Mock
    private IdempotencyRecordRepository repository;

    private final RecordingTransactionManager transactions = new RecordingTransactionManager();
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(
                repository, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC), transactions);
    }

    @Test
    void firstRequestClaimsTheKeyBeforeRunningTheActionAndStoresTheResponse() {
        when(repository.findByIdempotencyKeyAndActorScopeAndEndpointPattern("key-1", SCOPE, ENDPOINT))
                .thenReturn(Optional.empty());
        List<String> order = new ArrayList<>();
        when(repository.saveAndFlush(any(IdempotencyRecordEntity.class))).thenAnswer(inv -> {
            order.add("claim:" + ((IdempotencyRecordEntity) inv.getArgument(0)).getResponseStatus());
            return inv.getArgument(0);
        });

        Receipt result = service.executeIdempotently("key-1", SCOPE, ENDPOINT, PAYLOAD, Receipt.class, () -> {
            order.add("action");
            return new Receipt("r1", "500000.00");
        });

        assertThat(result).isEqualTo(new Receipt("r1", "500000.00"));
        assertThat(order).startsWith("claim:0", "action");
        ArgumentCaptor<IdempotencyRecordEntity> saved = ArgumentCaptor.forClass(IdempotencyRecordEntity.class);
        verify(repository, times(2)).saveAndFlush(saved.capture());
        IdempotencyRecordEntity stored = saved.getAllValues().get(1);
        assertThat(stored.getResponseStatus()).isEqualTo(201);
        assertThat(stored.getResponseBody()).contains("\"id\":\"r1\"");
        assertThat(stored.getRequestHash()).isEqualTo(IdempotencyService.sha256(PAYLOAD));
        assertThat(transactions.commits.get()).isEqualTo(1);
    }

    @Test
    void storedResponseIsReplayedWithoutRunningTheAction() {
        when(repository.findByIdempotencyKeyAndActorScopeAndEndpointPattern("key-1", SCOPE, ENDPOINT))
                .thenReturn(Optional.of(completedRecord(PAYLOAD)));

        Receipt result = service.executeIdempotently(
                "key-1", SCOPE, ENDPOINT, PAYLOAD, Receipt.class, failingAction());

        assertThat(result).isEqualTo(new Receipt("r1", "500000.00"));
        verify(repository, times(0)).saveAndFlush(any());
    }

    @Test
    void sameKeyWithADifferentPayloadIs409() {
        when(repository.findByIdempotencyKeyAndActorScopeAndEndpointPattern("key-1", SCOPE, ENDPOINT))
                .thenReturn(Optional.of(completedRecord("another payload")));

        assertProblem(
                () -> service.executeIdempotently("key-1", SCOPE, ENDPOINT, PAYLOAD, Receipt.class, failingAction()),
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_PAYLOAD_MISMATCH");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void missingKeyIs400BeforeAnything(String key) {
        assertProblem(
                () -> service.executeIdempotently(key, SCOPE, ENDPOINT, PAYLOAD, Receipt.class, failingAction()),
                HttpStatus.BAD_REQUEST,
                "IDEMPOTENCY_KEY_REQUIRED");
        verifyNoInteractions(repository);
        assertThat(transactions.begins.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"has space", "line\nbreak", "quote\"key", "slash/key", "khóa-tiếng-việt", "semi;colon"})
    void keyWithUnsafeCharactersIs400BeforeAnything(String key) {
        assertProblem(
                () -> service.executeIdempotently(key, SCOPE, ENDPOINT, PAYLOAD, Receipt.class, failingAction()),
                HttpStatus.BAD_REQUEST,
                "IDEMPOTENCY_KEY_INVALID");
        verifyNoInteractions(repository);
    }

    @Test
    void keyLongerThan128CharactersIs400() {
        assertProblem(
                () -> service.executeIdempotently(
                        "k".repeat(129), SCOPE, ENDPOINT, PAYLOAD, Receipt.class, failingAction()),
                HttpStatus.BAD_REQUEST,
                "IDEMPOTENCY_KEY_INVALID");
        verifyNoInteractions(repository);
    }

    @Test
    void keyOfExactly128SafeCharactersIsAccepted() {
        String key = "Aa0._:-".repeat(19).substring(0, 128);
        when(repository.findByIdempotencyKeyAndActorScopeAndEndpointPattern(key, SCOPE, ENDPOINT))
                .thenReturn(Optional.of(completedRecord(PAYLOAD)));

        assertThat(service.executeIdempotently(key, SCOPE, ENDPOINT, PAYLOAD, Receipt.class, failingAction()))
                .isEqualTo(new Receipt("r1", "500000.00"));
    }

    @Test
    void losingASameKeyRaceReplaysTheWinnersResponseOutsideTheFailedTransaction() {
        when(repository.findByIdempotencyKeyAndActorScopeAndEndpointPattern("key-1", SCOPE, ENDPOINT))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(completedRecord(PAYLOAD)));
        when(repository.saveAndFlush(any(IdempotencyRecordEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uq_idempotency_key_scope"));

        Receipt result = service.executeIdempotently(
                "key-1", SCOPE, ENDPOINT, PAYLOAD, Receipt.class, failingAction());

        assertThat(result).isEqualTo(new Receipt("r1", "500000.00"));
        assertThat(transactions.rollbacks.get()).isEqualTo(1);
        assertThat(transactions.begins.get()).isEqualTo(2); // the replay runs in a fresh transaction
    }

    @Test
    void losingASameKeyRaceWithoutAVisibleWinnerIs409InProgress() {
        when(repository.findByIdempotencyKeyAndActorScopeAndEndpointPattern("key-1", SCOPE, ENDPOINT))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(IdempotencyRecordEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uq_idempotency_key_scope"));

        assertProblem(
                () -> service.executeIdempotently("key-1", SCOPE, ENDPOINT, PAYLOAD, Receipt.class, failingAction()),
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_REQUEST_IN_PROGRESS");
    }

    @Test
    void violationRaisedByTheActionItselfIsNotMistakenForAKeyRace() {
        when(repository.findByIdempotencyKeyAndActorScopeAndEndpointPattern("key-1", SCOPE, ENDPOINT))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(IdempotencyRecordEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.executeIdempotently("key-1", SCOPE, ENDPOINT, PAYLOAD, Receipt.class, () -> {
                    throw new DataIntegrityViolationException("uq_orders_number");
                }))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_orders_number");
        assertThat(transactions.rollbacks.get()).isEqualTo(1);
    }

    @Test
    void failingActionRollsBackTheClaim() {
        when(repository.findByIdempotencyKeyAndActorScopeAndEndpointPattern("key-1", SCOPE, ENDPOINT))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(IdempotencyRecordEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        assertProblem(
                () -> service.executeIdempotently("key-1", SCOPE, ENDPOINT, PAYLOAD, Receipt.class, () -> {
                    throw FlowerChainException.conflict("REFUND_EXCEEDS_CAPTURED_AMOUNT", "t", "d");
                }),
                HttpStatus.CONFLICT,
                "REFUND_EXCEEDS_CAPTURED_AMOUNT");
        assertThat(transactions.rollbacks.get()).isEqualTo(1);
        assertThat(transactions.commits.get()).isZero();
    }

    private static IdempotencyRecordEntity completedRecord(String payload) {
        IdempotencyRecordEntity record = new IdempotencyRecordEntity(
                UUID.randomUUID(), "key-1", SCOPE, ENDPOINT, IdempotencyService.sha256(payload), 0, "{}",
                NOW.plusSeconds(3600), NOW);
        record.complete(201, "{\"id\":\"r1\",\"amount\":\"500000.00\"}", NOW);
        return record;
    }

    private static Supplier<Receipt> failingAction() {
        return () -> {
            throw new AssertionError("the action must not run");
        };
    }

    private static void assertProblem(Runnable call, HttpStatus status, String errorCode) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(FlowerChainException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getErrorCode()).isEqualTo(errorCode);
        });
    }

    /** Counts transaction boundaries; no real resources involved. */
    static final class RecordingTransactionManager implements PlatformTransactionManager {
        final AtomicInteger begins = new AtomicInteger();
        final AtomicInteger commits = new AtomicInteger();
        final AtomicInteger rollbacks = new AtomicInteger();

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            begins.incrementAndGet();
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            commits.incrementAndGet();
        }

        @Override
        public void rollback(TransactionStatus status) {
            rollbacks.incrementAndGet();
        }
    }
}
