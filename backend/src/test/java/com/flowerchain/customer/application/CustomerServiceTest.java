package com.flowerchain.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowerchain.audit.api.AuditApi;
import com.flowerchain.audit.api.AuditEntryCommand;
import com.flowerchain.customer.internal.CustomerEntity;
import com.flowerchain.customer.internal.CustomerRepository;
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

/** Customer login audit entries never contain the submitted email or password (TASK-018 defect 2). */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");
    private static final String EMAIL = "lan.nguyen@example.test";
    private static final String PASSWORD = "S3cret-Passw0rd";

    @Mock private CustomerRepository repository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditApi auditApi;

    @Captor private ArgumentCaptor<AuditEntryCommand> auditCaptor;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(repository, passwordEncoder, auditApi, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CustomerEntity customer(String status) {
        return new CustomerEntity(UUID.randomUUID(), EMAIL, "{bcrypt}hash", "Lan Nguyen", "0901234567", status, NOW);
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
        when(repository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD))
                .isInstanceOfSatisfying(FlowerChainException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));

        AuditEntryCommand audit = recordedAudit();
        assertThat(audit.category()).isEqualTo("LOGIN");
        assertThat(audit.action()).isEqualTo("CUSTOMER_LOGIN_FAILURE");
        assertThat(audit.targetEntityId()).isNull();
        assertThat(audit.newValuesJson()).isEqualTo("{\"reason\":\"UNKNOWN_ACCOUNT\"}");
        assertContainsNoCredentials(audit);
    }

    @Test
    void wrongPasswordIsAuditedAgainstTheAccountIdWithoutTheEmailOrPassword() {
        CustomerEntity existing = customer("ACTIVE");
        when(repository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches(PASSWORD, "{bcrypt}hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login("  " + EMAIL.toUpperCase() + " ", PASSWORD))
                .isInstanceOf(FlowerChainException.class);

        AuditEntryCommand audit = recordedAudit();
        assertThat(audit.targetEntityId()).isEqualTo(existing.getId());
        assertThat(audit.newValuesJson()).isEqualTo("{\"reason\":\"INVALID_PASSWORD\"}");
        assertContainsNoCredentials(audit);
    }

    @Test
    void disabledAccountIsAuditedWithItsOwnReasonCode() {
        CustomerEntity disabled = customer("DISABLED");
        when(repository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD)).isInstanceOf(FlowerChainException.class);

        AuditEntryCommand audit = recordedAudit();
        assertThat(audit.targetEntityId()).isEqualTo(disabled.getId());
        assertThat(audit.newValuesJson()).isEqualTo("{\"reason\":\"ACCOUNT_NOT_ACTIVE\"}");
        assertContainsNoCredentials(audit);
    }

    @Test
    void successfulLoginIsAuditedWithoutCredentials() {
        CustomerEntity existing = customer("ACTIVE");
        when(repository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches(PASSWORD, "{bcrypt}hash")).thenReturn(true);

        assertThat(service.login(EMAIL, PASSWORD).accessToken()).startsWith("cust_");

        AuditEntryCommand audit = recordedAudit();
        assertThat(audit.action()).isEqualTo("CUSTOMER_LOGIN_SUCCESS");
        assertThat(audit.actorId()).isEqualTo(existing.getId());
        assertThat(audit.newValuesJson()).isNull();
    }

    @Test
    void failedLoginAuditIsNotRolledBackWithTheUnauthorizedException() throws NoSuchMethodException {
        Transactional tx = CustomerService.class.getMethod("login", String.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.noRollbackFor()).contains(FlowerChainException.class);
    }
}
