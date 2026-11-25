package com.flowerchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowerchain.identity.application.IdentityService;
import com.flowerchain.support.PostgresIntegrationTestSupport;
import com.flowerchain.support.TestData;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * TEST-013 / TEST-014 (REQ-011, REQ-012) end to end: a branch manager of branch A logs in for real and is denied
 * branch B's data through the path, the query and the body, while lists only return branch A. CI only (Docker).
 */
class BranchIsolationIT extends PostgresIntegrationTestSupport {

    @Autowired private WebApplicationContext webContext;
    @Autowired private IdentityService identityService;

    private MockMvc mvc;
    private UUID branchA;
    private UUID branchB;
    private UUID branchC;
    private UUID product;
    private UUID orderAtA;
    private UUID orderAtB;
    private String managerAToken;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
        branchA = data.branch("Isolation A");
        branchB = data.branch("Isolation B");
        branchC = data.branch("Isolation C");
        product = data.product(TestData.unique("SKU"), "Gerbera");
        data.stock(branchA, product, 5);
        data.stock(branchB, product, 7);
        UUID customer = data.customer();
        orderAtA = data.order(customer, branchA, product, "PICKUP", "CONFIRMED", "100000.00", 1, "0.00", Instant.now());
        orderAtB = data.order(customer, branchB, product, "PICKUP", "CONFIRMED", "100000.00", 1, "0.00", Instant.now());

        String email = TestData.unique("manager-a") + "@flowerchain.test";
        identityService.createStaff(email, "Manager-A-Passw0rd", "Manager A", null,
                Set.of("BRANCH_MANAGER"), Set.of(branchA), null);
        managerAToken = identityService.login(email, "Manager-A-Passw0rd").accessToken();
    }

    private String bearer() {
        return "Bearer " + managerAToken;
    }

    @Test
    void unauthenticatedAdminCallGets401ProblemJson() throws Exception {
        mvc.perform(get("/api/v1/admin/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void otherBranchIdInThePathIsDenied() throws Exception {
        mvc.perform(get("/api/v1/admin/branches/{branchId}/inventory", branchB).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("BRANCH_ACCESS_DENIED"));
        mvc.perform(post("/api/v1/admin/branches/{branchId}/stock-adjustments", branchB)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + product + "\",\"adjustmentType\":\"SPOILAGE\","
                                + "\"quantityDelta\":-7,\"reasonNote\":\"forged write-off\"}"))
                .andExpect(status().isForbidden());

        assertThat(data.availableStock(branchB, product)).isEqualTo(7);
    }

    @Test
    void otherBranchIdInTheQueryIsDenied() throws Exception {
        mvc.perform(get("/api/v1/admin/orders").param("branchId", branchB.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reports/revenue").param("branchId", branchB.toString())
                        .param("from", "2026-01-01T00:00:00Z").param("to", "2027-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("BRANCH_ACCESS_DENIED"));
    }

    @Test
    void otherBranchIdsInTheBodyAreDenied() throws Exception {
        mvc.perform(post("/api/v1/admin/stock-transfers")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceBranchId\":\"" + branchB + "\",\"targetBranchId\":\"" + branchC
                                + "\",\"lines\":[{\"productId\":\"" + product + "\",\"requestedQuantity\":2}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("BRANCH_ACCESS_DENIED"));

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM stock_transfers WHERE source_branch_id = ?", Integer.class, branchB))
                .isZero();
    }

    @Test
    void otherBranchOrderCannotBeReadOrChanged() throws Exception {
        mvc.perform(get("/api/v1/admin/orders/{orderId}", orderAtB).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().is(anyOf(is(403), is(404))));
        mvc.perform(post("/api/v1/admin/orders/{orderId}/status", orderAtB)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"CANCELLED\",\"note\":\"forged\"}"))
                .andExpect(status().is(anyOf(is(403), is(404))));

        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderAtB))
                .isEqualTo("CONFIRMED");
    }

    @Test
    void orderListOnlyContainsTheCallersBranch() throws Exception {
        mvc.perform(get("/api/v1/admin/orders").param("size", "100").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].processingBranchId", everyItem(is(branchA.toString()))))
                .andExpect(jsonPath("$.items[*].id", hasItem(orderAtA.toString())))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(orderAtB.toString()))));
    }
}
