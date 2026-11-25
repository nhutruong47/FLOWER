package com.flowerchain.shared.api;

import java.util.Set;
import java.util.UUID;

public record FlowerChainPrincipal(
        UUID userId,
        String email,
        String actorType,
        Set<String> roles,
        boolean globalAccess,
        Set<UUID> authorizedBranchIds) {

    public static FlowerChainPrincipal staff(
            UUID userId, String email, Set<String> roles, Set<UUID> authorizedBranchIds) {
        boolean isGlobal = roles.contains("SYSTEM_OWNER") || roles.contains("HEAD_OFFICE_ADMIN");
        return new FlowerChainPrincipal(
                userId,
                email,
                "STAFF",
                Set.copyOf(roles),
                isGlobal,
                Set.copyOf(authorizedBranchIds));
    }

    public static FlowerChainPrincipal customer(UUID customerId, String email) {
        return new FlowerChainPrincipal(
                customerId,
                email,
                "CUSTOMER",
                Set.of("CUSTOMER"),
                false,
                Set.of());
    }

    /**
     * The authenticated actor's ID, as recorded in audit trails and actor columns: the staff user
     * ID for staff principals and the customer ID for customer principals. Alias of {@link #userId()}.
     */
    public UUID actorId() {
        return userId;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean canAccessBranch(UUID branchId) {
        if (branchId == null) {
            return false;
        }
        return globalAccess || authorizedBranchIds.contains(branchId);
    }
}
