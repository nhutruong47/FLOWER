package com.flowerchain.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowerchain.support.TestPrincipals;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Branch-level authorization rules (REQ-011, REQ-012). TEST-013, TEST-014 (unit level).
 *
 * <p>The branches a user may act on always come from the authenticated principal on the server; a branch ID sent by
 * the client is only ever checked against that list.
 */
class BranchAuthorizationGuardTest {

    private static final UUID BRANCH_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BRANCH_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID BRANCH_C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private final BranchAuthorizationGuard guard = new BranchAuthorizationGuard();

    @AfterEach
    void clearSecurityContext() {
        TestPrincipals.clear();
    }

    @Nested
    class Authentication {

        @Test
        void noAuthenticationIsRejectedWith401() {
            assertThatThrownBy(guard::requirePrincipal)
                    .isInstanceOfSatisfying(FlowerChainException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(ex.getErrorCode()).isEqualTo("AUTH_UNAUTHORIZED");
                    });
        }

        @Test
        void anonymousAuthenticationIsRejectedWith401() {
            SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                    "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

            assertThatThrownBy(guard::requireStaffPrincipal)
                    .isInstanceOfSatisfying(FlowerChainException.class,
                            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        @Test
        void customerIsNotStaff() {
            TestPrincipals.authenticate(TestPrincipals.customer());

            assertThatThrownBy(guard::requireStaffPrincipal)
                    .isInstanceOfSatisfying(FlowerChainException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                        assertThat(ex.getErrorCode()).isEqualTo("AUTH_FORBIDDEN_ROLE");
                    });
        }

        @Test
        void staffIsNotCustomer() {
            TestPrincipals.authenticate(TestPrincipals.staff("SALES_STAFF", BRANCH_A));

            assertThatThrownBy(guard::requireCustomerPrincipal)
                    .isInstanceOfSatisfying(FlowerChainException.class,
                            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    @Nested
    class Roles {

        @Test
        void requireAnyRoleAcceptsAListedRole() {
            FlowerChainPrincipal manager = TestPrincipals.staff("BRANCH_MANAGER", BRANCH_A);
            TestPrincipals.authenticate(manager);

            assertThat(guard.requireAnyRole("HEAD_OFFICE_ADMIN", "BRANCH_MANAGER")).isEqualTo(manager);
        }

        @Test
        void requireAnyRoleRejectsAnUnlistedRole() {
            TestPrincipals.authenticate(TestPrincipals.staff("DELIVERY_STAFF", BRANCH_A));

            assertThatThrownBy(() -> guard.requireAnyRole("HEAD_OFFICE_ADMIN", "BRANCH_MANAGER"))
                    .isInstanceOfSatisfying(FlowerChainException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                        assertThat(ex.getErrorCode()).isEqualTo("AUTH_FORBIDDEN_ROLE");
                    });
        }

        @Test
        void branchManagerIsNotHeadOffice() {
            TestPrincipals.authenticate(TestPrincipals.staff("BRANCH_MANAGER", BRANCH_A));

            assertThatThrownBy(guard::requireGlobalHeadOfficeAccess)
                    .isInstanceOfSatisfying(FlowerChainException.class,
                            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }

        @Test
        void headOfficeAdminAndSystemOwnerHaveGlobalAccess() {
            TestPrincipals.authenticate(TestPrincipals.staff("HEAD_OFFICE_ADMIN"));
            assertThat(guard.requireGlobalHeadOfficeAccess().globalAccess()).isTrue();

            TestPrincipals.authenticate(TestPrincipals.staff("SYSTEM_OWNER"));
            assertThat(guard.requireGlobalHeadOfficeAccess().globalAccess()).isTrue();
        }
    }

    @Nested
    class AssertCanAccessBranch {

        @Test
        void branchStaffCanAccessTheirOwnBranch() {
            TestPrincipals.authenticate(TestPrincipals.staff("INVENTORY_STAFF", BRANCH_A));

            assertThat(guard.assertCanAccessBranch(BRANCH_A).authorizedBranchIds()).containsExactly(BRANCH_A);
        }

        @Test
        void branchStaffAreDeniedAnotherBranch() {
            TestPrincipals.authenticate(TestPrincipals.staff("BRANCH_MANAGER", BRANCH_A));

            assertThatThrownBy(() -> guard.assertCanAccessBranch(BRANCH_B))
                    .isInstanceOfSatisfying(FlowerChainException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                        assertThat(ex.getErrorCode()).isEqualTo("BRANCH_ACCESS_DENIED");
                    });
        }

        @Test
        void missingBranchIdIsABadRequest() {
            TestPrincipals.authenticate(TestPrincipals.staff("BRANCH_MANAGER", BRANCH_A));

            assertThatThrownBy(() -> guard.assertCanAccessBranch(null))
                    .isInstanceOfSatisfying(FlowerChainException.class,
                            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }

        @Test
        void globalStaffCanAccessAnyBranch() {
            TestPrincipals.authenticate(TestPrincipals.staff("HEAD_OFFICE_ADMIN"));

            assertThat(guard.assertCanAccessBranch(BRANCH_B)).isNotNull();
            assertThat(guard.assertCanAccessBranch(BRANCH_C)).isNotNull();
        }

        @Test
        void customersCannotUseBranchEndpointsEvenWithoutABranchList() {
            TestPrincipals.authenticate(TestPrincipals.customer());

            assertThatThrownBy(() -> guard.assertCanAccessBranch(BRANCH_A))
                    .isInstanceOfSatisfying(FlowerChainException.class,
                            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    @Nested
    class ResolveEffectiveBranchFilter {

        private final Set<UUID> allActiveBranches = Set.of(BRANCH_A, BRANCH_B, BRANCH_C);

        @Test
        void branchStaffWithoutFilterSeeOnlyTheirAssignedBranches() {
            TestPrincipals.authenticate(TestPrincipals.staff("SALES_STAFF", BRANCH_A));

            assertThat(guard.resolveEffectiveBranchFilter(Optional.empty(), allActiveBranches))
                    .containsExactly(BRANCH_A);
        }

        @Test
        void branchStaffWithMultipleAssignmentsSeeExactlyThoseBranches() {
            TestPrincipals.authenticate(TestPrincipals.staff("BRANCH_MANAGER", BRANCH_A, BRANCH_C));

            assertThat(guard.resolveEffectiveBranchFilter(Optional.empty(), allActiveBranches))
                    .containsExactlyInAnyOrder(BRANCH_A, BRANCH_C);
        }

        @Test
        void branchStaffFilteringOnTheirOwnBranchGetThatBranch() {
            TestPrincipals.authenticate(TestPrincipals.staff("BRANCH_MANAGER", BRANCH_A, BRANCH_C));

            assertThat(guard.resolveEffectiveBranchFilter(Optional.of(BRANCH_C), allActiveBranches))
                    .containsExactly(BRANCH_C);
        }

        @Test
        void forgedBranchFilterIsRejectedNotIgnored() {
            TestPrincipals.authenticate(TestPrincipals.staff("BRANCH_MANAGER", BRANCH_A));

            assertThatThrownBy(() -> guard.resolveEffectiveBranchFilter(Optional.of(BRANCH_B), allActiveBranches))
                    .isInstanceOfSatisfying(FlowerChainException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                        assertThat(ex.getErrorCode()).isEqualTo("BRANCH_ACCESS_DENIED");
                    });
        }

        @Test
        void globalStaffWithoutFilterSeeAllActiveBranches() {
            TestPrincipals.authenticate(TestPrincipals.staff("HEAD_OFFICE_ADMIN"));

            assertThat(guard.resolveEffectiveBranchFilter(Optional.empty(), allActiveBranches))
                    .containsExactlyInAnyOrderElementsOf(allActiveBranches);
        }

        @Test
        void globalStaffWithFilterSeeOnlyThatBranch() {
            TestPrincipals.authenticate(TestPrincipals.staff("SYSTEM_OWNER"));

            assertThat(guard.resolveEffectiveBranchFilter(Optional.of(BRANCH_B), allActiveBranches))
                    .containsExactly(BRANCH_B);
        }

        @Test
        void customersCannotResolveABranchFilter() {
            TestPrincipals.authenticate(TestPrincipals.customer());

            assertThatThrownBy(() -> guard.resolveEffectiveBranchFilter(Optional.empty(), allActiveBranches))
                    .isInstanceOfSatisfying(FlowerChainException.class,
                            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }
    }
}
