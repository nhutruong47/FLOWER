package com.flowerchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowerchain.customer.application.CustomerService;
import com.flowerchain.identity.application.IdentityService;
import com.flowerchain.shared.api.FlowerChainException;
import com.flowerchain.support.PostgresIntegrationTestSupport;
import com.flowerchain.support.TestData;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * TASK-018 defect 2: failed logins leave a committed audit entry (reason code, and the account ID when the account
 * exists) and no audit row ever contains the submitted email or password. CI only (Docker).
 */
class LoginAuditIT extends PostgresIntegrationTestSupport {

    @Autowired private CustomerService customerService;
    @Autowired private IdentityService identityService;

    @Test
    void failedCustomerLoginsAreAuditedWithoutTheEmailOrPassword() {
        String email = TestData.unique("lan") + "@example.test";
        UUID customerId = data.customer(email, "Correct-Passw0rd", "ACTIVE");
        String unknownEmail = TestData.unique("nobody") + "@example.test";

        assertThatThrownBy(() -> customerService.login(email, "Wrong-Passw0rd")).isInstanceOf(FlowerChainException.class);
        assertThatThrownBy(() -> customerService.login(unknownEmail, "Wrong-Passw0rd"))
                .isInstanceOf(FlowerChainException.class);

        List<Map<String, Object>> known = auditRows("CUSTOMER_LOGIN_FAILURE", customerId);
        assertThat(known).singleElement()
                .satisfies(row -> assertThat(row.get("new_values")).isEqualTo("{\"reason\":\"INVALID_PASSWORD\"}"));
        assertNoAuditRowMentions(email, unknownEmail, "Wrong-Passw0rd");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_logs WHERE action = 'CUSTOMER_LOGIN_FAILURE' "
                                + "AND target_entity_id IS NULL AND new_values = ?",
                        Integer.class,
                        "{\"reason\":\"UNKNOWN_ACCOUNT\"}"))
                .isPositive();
    }

    @Test
    void failedStaffLoginsAreAuditedWithoutTheEmailOrPassword() {
        String email = TestData.unique("manager") + "@flowerchain.test";
        UUID staffId = identityService
                .createStaff(email, "Correct-Passw0rd", "Manager", null, Set.of("HEAD_OFFICE_ADMIN"), Set.of(), null)
                .id();

        assertThatThrownBy(() -> identityService.login(email, "Wrong-Passw0rd")).isInstanceOf(FlowerChainException.class);

        assertThat(auditRows("STAFF_LOGIN_FAILURE", staffId)).singleElement()
                .satisfies(row -> assertThat(row.get("new_values")).isEqualTo("{\"reason\":\"INVALID_PASSWORD\"}"));
        assertNoAuditRowMentions(email, "Wrong-Passw0rd");
    }

    private List<Map<String, Object>> auditRows(String action, UUID targetId) {
        return jdbc.queryForList(
                "SELECT * FROM audit_logs WHERE category = 'LOGIN' AND action = ? AND target_entity_id = ?",
                action, targetId);
    }

    private void assertNoAuditRowMentions(String... secrets) {
        for (String secret : secrets) {
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM audit_logs "
                                    + "WHERE strpos(coalesce(old_values, '') || coalesce(new_values, ''), ?) > 0",
                            Integer.class, secret))
                    .as("audit rows containing '%s'", secret)
                    .isZero();
        }
    }
}
