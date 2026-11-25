package com.flowerchain.shared.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class BranchAuthorizationGuard {

    public FlowerChainPrincipal requirePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof FlowerChainPrincipal principal)) {
            throw FlowerChainException.unauthorized("AUTH_UNAUTHORIZED", "Authentication is required.");
        }
        return principal;
    }

    public FlowerChainPrincipal requireStaffPrincipal() {
        FlowerChainPrincipal principal = requirePrincipal();
        if (!"STAFF".equals(principal.actorType())) {
            throw FlowerChainException.forbidden("AUTH_FORBIDDEN_ROLE", "Staff access is required.");
        }
        return principal;
    }

    public FlowerChainPrincipal requireCustomerPrincipal() {
        FlowerChainPrincipal principal = requirePrincipal();
        if (!"CUSTOMER".equals(principal.actorType())) {
            throw FlowerChainException.forbidden("AUTH_FORBIDDEN_ROLE", "Customer access is required.");
        }
        return principal;
    }

    public FlowerChainPrincipal requireGlobalHeadOfficeAccess() {
        FlowerChainPrincipal principal = requireStaffPrincipal();
        if (!principal.globalAccess()) {
            throw FlowerChainException.forbidden(
                    "AUTH_FORBIDDEN_ROLE",
                    "This operation requires Head Office or System Owner privileges.");
        }
        return principal;
    }

    public FlowerChainPrincipal requireAnyRole(String... allowedRoles) {
        FlowerChainPrincipal principal = requireStaffPrincipal();
        for (String role : allowedRoles) {
            if (principal.hasRole(role)) {
                return principal;
            }
        }
        throw FlowerChainException.forbidden(
                "AUTH_FORBIDDEN_ROLE",
                "Your role is not permitted to perform this operation.");
    }

    public FlowerChainPrincipal assertCanAccessBranch(UUID targetBranchId) {
        FlowerChainPrincipal principal = requireStaffPrincipal();
        if (targetBranchId == null) {
            throw FlowerChainException.badRequest("BRANCH_ID_REQUIRED", "Branch ID is required.");
        }
        if (!principal.canAccessBranch(targetBranchId)) {
            throw FlowerChainException.forbidden(
                    "BRANCH_ACCESS_DENIED",
                    "You are not authorized to access or modify resources belonging to branch " + targetBranchId + ".");
        }
        return principal;
    }

    public Set<UUID> resolveEffectiveBranchFilter(Optional<UUID> requestedBranchId, Set<UUID> allActiveBranchIds) {
        FlowerChainPrincipal principal = requireStaffPrincipal();
        if (requestedBranchId.isPresent()) {
            UUID branchId = requestedBranchId.get();
            assertCanAccessBranch(branchId);
            return Set.of(branchId);
        }
        if (principal.globalAccess()) {
            return allActiveBranchIds;
        }
        return principal.authorizedBranchIds();
    }
}
