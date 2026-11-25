package com.flowerchain.shared.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerchain.shared.api.FlowerChainException;
import com.flowerchain.shared.api.IdempotencyExecutor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService implements IdempotencyExecutor {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyService(
            IdempotencyRecordRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public <T> T executeIdempotently(
            String idempotencyKey,
            String actorScope,
            String endpointPattern,
            String requestPayloadSignature,
            Class<T> responseType,
            Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw FlowerChainException.badRequest(
                    "IDEMPOTENCY_KEY_REQUIRED", "Header 'Idempotency-Key' is required.");
        }
        String trimmedKey = idempotencyKey.trim();
        String hash = sha256(requestPayloadSignature);
        Optional<IdempotencyRecordEntity> existing =
                repository.findByIdempotencyKeyAndActorScopeAndEndpointPattern(
                        trimmedKey, actorScope, endpointPattern);
        if (existing.isPresent()) {
            IdempotencyRecordEntity record = existing.get();
            if (!record.getRequestHash().equals(hash)) {
                throw FlowerChainException.conflict(
                        "IDEMPOTENCY_KEY_PAYLOAD_MISMATCH",
                        "Idempotency key payload mismatch",
                        "The supplied Idempotency-Key was already used with a different request payload.");
            }
            try {
                return objectMapper.readValue(record.getResponseBody(), responseType);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize idempotent response", e);
            }
        }

        T result = action.get();
        try {
            String serialized = objectMapper.writeValueAsString(result);
            Instant now = Instant.now(clock);
            repository.save(new IdempotencyRecordEntity(
                    UUID.randomUUID(),
                    trimmedKey,
                    actorScope,
                    endpointPattern,
                    hash,
                    201,
                    serialized,
                    now.plus(Duration.ofHours(24)),
                    now));
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize idempotent response", e);
        }
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
