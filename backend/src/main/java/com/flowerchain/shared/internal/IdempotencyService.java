package com.flowerchain.shared.internal;

import com.flowerchain.shared.api.FlowerChainException;
import com.flowerchain.shared.api.IdempotencyExecutor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs a retryable operation at most once per {@code Idempotency-Key}, actor and endpoint.
 *
 * <p>Each call runs in its own transaction ({@code REQUIRES_NEW}):
 *
 * <ol>
 *   <li>the key is validated before anything else (1-128 characters of {@code [A-Za-z0-9._:-]});
 *   <li>a stored response for the key is replayed (409 if the request payload differs);
 *   <li>otherwise the key is claimed by inserting its record and flushing <em>before</em> the action runs, so a
 *       concurrent request with the same key blocks on the unique key until this transaction ends;
 *   <li>the action runs and its response is stored in the same transaction. If the action fails, the claim is
 *       rolled back with it.
 * </ol>
 *
 * <p>The loser of a same-key race fails on the unique constraint without having run the action. That violation is
 * handled outside the failed transaction: the winner's stored response is replayed, or 409
 * {@code IDEMPOTENCY_REQUEST_IN_PROGRESS} is returned if none is visible.
 */
@Service
public class IdempotencyService implements IdempotencyExecutor {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:\\-]{1,128}");
    private static final Duration RETENTION = Duration.ofHours(24);
    private static final int STORED_RESPONSE_STATUS = 201;
    /** Status of a claimed key whose action is still running; never visible to other transactions. */
    private static final int PENDING_RESPONSE_STATUS = 0;
    private static final String PENDING_RESPONSE_BODY = "{}";

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate requiresNew;

    public IdempotencyService(
            IdempotencyRecordRepository repository,
            ObjectMapper objectMapper,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public <T> T executeIdempotently(
            String idempotencyKey,
            String actorScope,
            String endpointPattern,
            String requestPayloadSignature,
            Class<T> responseType,
            Supplier<T> action) {
        String key = validateKey(idempotencyKey);
        String hash = sha256(requestPayloadSignature);
        AtomicBoolean keyClaimed = new AtomicBoolean(false);
        try {
            return requiresNew.execute(status -> {
                var existing = repository.findByIdempotencyKeyAndActorScopeAndEndpointPattern(
                        key, actorScope, endpointPattern);
                if (existing.isPresent()) {
                    return replay(existing.get(), hash, responseType);
                }
                Instant now = Instant.now(clock);
                IdempotencyRecordEntity record = repository.saveAndFlush(new IdempotencyRecordEntity(
                        UUID.randomUUID(),
                        key,
                        actorScope,
                        endpointPattern,
                        hash,
                        PENDING_RESPONSE_STATUS,
                        PENDING_RESPONSE_BODY,
                        now.plus(RETENTION),
                        now));
                keyClaimed.set(true);

                T result = action.get();
                record.complete(STORED_RESPONSE_STATUS, serialize(result), Instant.now(clock));
                repository.saveAndFlush(record);
                return result;
            });
        } catch (DataIntegrityViolationException violation) {
            if (keyClaimed.get()) {
                throw violation; // raised by the action itself, not by the key claim
            }
            // Another request with the same key committed first; this transaction never ran the action.
            return requiresNew.execute(status -> repository
                    .findByIdempotencyKeyAndActorScopeAndEndpointPattern(key, actorScope, endpointPattern)
                    .map(record -> replay(record, hash, responseType))
                    .orElseThrow(IdempotencyService::requestInProgress));
        }
    }

    private String validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw FlowerChainException.badRequest(
                    "IDEMPOTENCY_KEY_REQUIRED", "Header 'Idempotency-Key' is required.");
        }
        String key = idempotencyKey.strip();
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw FlowerChainException.badRequest(
                    "IDEMPOTENCY_KEY_INVALID",
                    "Header 'Idempotency-Key' must be 1 to 128 characters of letters, digits and . _ : -");
        }
        return key;
    }

    private <T> T replay(IdempotencyRecordEntity record, String hash, Class<T> responseType) {
        if (!record.getRequestHash().equals(hash)) {
            throw FlowerChainException.conflict(
                    "IDEMPOTENCY_KEY_PAYLOAD_MISMATCH",
                    "Idempotency key payload mismatch",
                    "The supplied Idempotency-Key was already used with a different request payload.");
        }
        if (record.getResponseStatus() == PENDING_RESPONSE_STATUS) {
            throw requestInProgress();
        }
        try {
            return objectMapper.readValue(record.getResponseBody(), responseType);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize idempotent response", e);
        }
    }

    private String serialize(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize idempotent response", e);
        }
    }

    private static FlowerChainException requestInProgress() {
        return FlowerChainException.conflict(
                "IDEMPOTENCY_REQUEST_IN_PROGRESS",
                "Request in progress",
                "A request with this Idempotency-Key is still being processed. Retry later with the same key.");
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
