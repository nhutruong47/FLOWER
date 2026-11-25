package com.flowerchain.support;

import com.flowerchain.shared.api.FlowerChainPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Test helpers that build principals the same way the token resolvers do and place them in the security context. */
public final class TestPrincipals {

    private TestPrincipals() {}

    public static FlowerChainPrincipal staff(String role, UUID... branchIds) {
        return FlowerChainPrincipal.staff(UUID.randomUUID(), role.toLowerCase() + "@example.test", Set.of(role), Set.of(branchIds));
    }

    public static FlowerChainPrincipal customer() {
        return FlowerChainPrincipal.customer(UUID.randomUUID(), "customer@example.test");
    }

    public static void authenticate(FlowerChainPrincipal principal) {
        List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
