package com.flowerchain.shared.api;

import java.util.function.Supplier;

public interface IdempotencyExecutor {

    <T> T executeIdempotently(
            String idempotencyKey,
            String actorScope,
            String endpointPattern,
            String requestPayloadSignature,
            Class<T> responseType,
            Supplier<T> action);
}
