package com.flowerchain.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowerchain.audit.api.AuditApi;
import com.flowerchain.audit.api.AuditEntryCommand;
import com.flowerchain.branch.api.BranchApi;
import com.flowerchain.identity.internal.StaffBranchAssignmentRepository;
import com.flowerchain.identity.internal.StaffRefreshSessionRepository;
import com.flowerchain.identity.internal.StaffUserEntity;
import com.flowerchain.identity.internal.StaffUserRepository;
import com.flowerchain.identity.internal.StaffUserRoleRepository;
import com.flowerchain.shared.api.FlowerChainException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Staff login audit entries never contain the submitted email or password (TASK-018 defect 2). */
@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");
    private static final String EMAIL = "manager.a@flowerchain.test";
    private static final String PASSWORD = "Branch-A-Passw0rd";

    @Mock private StaffUserRepository userRepository;
    @Mock private StaffUserRoleRepository roleRepository;
    @Mock private StaffBranchAssignmentRepository branchAssignmentRepository;
    @Mock private StaffRefreshSessionRepository sessionRepository;
    @Mock private BranchApi branchApi;
    @Mock private AuditApi auditApi;
    @Mock private PasswordEncoder passwordEncoder;

    @Captor private ArgumentCaptor<AuditEntryCommand> auditCaptor;

    private IdentityService service;

    @BeforeEach
    void setUp() {
        service = new IdentityService(
                userRepository,
                roleRepository,
                branchAssignmentRepository,
                sessionRepository,
                branchApi,
                auditApi,
                passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static StaffUserEntity staff(String status) {
        return new StaffUserEntity(UUID.randomUUID(), EMAIL, "{bcrypt}hash", "Manager A", null, status, NOW);
    }

    private AuditEntryCommand recordedAudit() {
        verify(auditApi).record(auditCaptor.capture());
        return auditCaptor.getValue();
    }

    private static void assertContainsNoCredentials(AuditEntryCommand audit) {
        String values = String.valueOf(audit.oldValuesJson()) + audit.newValuesJson();
        assertThat(values).doesNotContainIgnoringCase(EMAIL).doesNotContain("@").doesNotContain(PASSWORD);
    }

    @Test
    void unknownEmailIsAuditedWithAReasonCodeButWithoutTheEmail() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD))
                .isInstanceOfSatisfying(FlowerChainException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));

        AuditEntryCommand audit = recordedAudit();
        assertThat(audit.category()).isEqualTo("LOGIN");
        assertThat(audit.action()).isEqualTo("STAFF_LOGIN_FAILURE");
        assertThat(audit.targetEntityId()).isNull();
        assertThat(audit.newValuesJson()).isEqualTo("{\"reason\":\"UNKNOWN_ACCOUNT\"}");
        assertContainsNoCredentials(audit);
        verifyNoInteractions(sessionRepository);
    }

    @Test
    void wrongPasswordIsAuditedAgainstTheAccountIdWithoutTheEmailOrPassword() {
        StaffUserEntity existing = staff("ACTIVE");
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches(PASSWORD, "{bcrypt}hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD)).isInstanceOf(FlowerChainException.class);

        AuditEntryCommand audit = recordedAudit();
        assertThat(audit.targetEntityId()).isEqualTo(existing.getId());
        assertThat(audit.newValuesJson()).isEqualTo("{\"reason\":\"INVALID_PASSWORD\"}");
        assertContainsNoCredentials(audit);
        verifyNoInteractions(sessionRepository);
    }

    @Test
    void disabledAccountIsAuditedWithItsOwnReasonCode() {
        StaffUserEntity disabled = staff("DISABLED");
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD)).isInstanceOf(FlowerChainException.class);

        AuditEntryCommand audit = recordedAudit();
        assertThat(audit.targetEntityId()).isEqualTo(disabled.getId());
        assertThat(audit.newValuesJson()).isEqualTo("{\"reason\":\"ACCOUNT_NOT_ACTIVE\"}");
        assertContainsNoCredentials(audit);
    }

    @Test
    void failedLoginAuditIsNotRolledBackWithTheUnauthorizedException() throws NoSuchMethodException {
        Transactional tx = IdentityService.class.getMethod("login", String.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.noRollbackFor()).contains(FlowerChainException.class);
    }
}
