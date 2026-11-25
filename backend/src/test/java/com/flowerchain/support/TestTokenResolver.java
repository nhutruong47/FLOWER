package com.flowerchain.support;

import com.flowerchain.shared.api.FlowerChainPrincipal;
import com.flowerchain.shared.security.TokenPrincipalResolver;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Token resolver for web security tests: fixed tokens map to fixed principals, and every lookup is counted. */
public class TestTokenResolver implements TokenPrincipalResolver {

    private final Map<String, FlowerChainPrincipal> principalsByToken = new ConcurrentHashMap<>();
    private final AtomicInteger lookups = new AtomicInteger();

    public TestTokenResolver register(String token, FlowerChainPrincipal principal) {
        principalsByToken.put(token, principal);
        return this;
    }

    @Override
    public Optional<FlowerChainPrincipal> resolveByToken(String rawToken) {
        lookups.incrementAndGet();
        return Optional.ofNullable(principalsByToken.get(rawToken));
    }

    public int lookups() {
        return lookups.get();
    }

    public void resetLookups() {
        lookups.set(0);
    }
}
