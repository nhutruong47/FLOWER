package com.flowerchain.reporting.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowerchain.branch.api.BranchApi;
import com.flowerchain.branch.api.BranchDto;
import com.flowerchain.order.api.OrderApi;
import com.flowerchain.order.api.OrderApi.OrderDetailDto;
import com.flowerchain.order.api.OrderApi.OrderLineSnapshotDto;
import com.flowerchain.reporting.application.RevenueReportService;
import com.flowerchain.shared.api.BranchAuthorizationGuard;
import com.flowerchain.shared.security.SecurityConfig;
import com.flowerchain.shared.web.GlobalExceptionHandler;
import com.flowerchain.support.TestPrincipals;
import com.flowerchain.support.TestTokenResolver;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * {@code GET /api/v1/admin/reports/revenue} over HTTP with the real security chain, guard, service and exception
 * handler; only the order and branch module APIs are mocked. FR-034 AC1/AC2, REQ-011/REQ-012 (TEST-013/TEST-014).
 */
@SpringJUnitWebConfig(RevenueReportControllerTest.TestConfig.class)
@TestPropertySource(properties = "flowerchain.security.cors-allowed-origins=http://localhost:5173")
class RevenueReportControllerTest {

    static final UUID BRANCH_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID BRANCH_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    static final Instant TO = Instant.parse("2026-12-01T00:00:00Z");
    static final String URL = "/api/v1/admin/reports/revenue";

    static final String HEAD_OFFICE = "staff_head_office";
    static final String MANAGER_A = "staff_manager_a";
    static final String SALES_A = "staff_sales_a";
    static final String INVENTORY_A = "staff_inventory_a";

    @Configuration
    @EnableWebMvc
    @Import({
        SecurityConfig.class,
        BranchAuthorizationGuard.class,
        GlobalExceptionHandler.class,
        RevenueReportController.class,
        RevenueReportService.class
    })
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-25T08:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        TestTokenResolver testTokenResolver() {
            return new TestTokenResolver()
                    .register(HEAD_OFFICE, TestPrincipals.staff("HEAD_OFFICE_ADMIN"))
                    .register(MANAGER_A, TestPrincipals.staff("BRANCH_MANAGER", BRANCH_A))
                    .register(SALES_A, TestPrincipals.staff("SALES_STAFF", BRANCH_A))
                    .register(INVENTORY_A, TestPrincipals.staff("INVENTORY_STAFF", BRANCH_A));
        }
    }

    @MockitoBean private OrderApi orderApi;
    @MockitoBean private BranchApi branchApi;

    @Autowired private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void givenActiveBranches() {
        when(branchApi.listActiveBranches()).thenReturn(List.of(branch(BRANCH_A, "Alpha"), branch(BRANCH_B, "Beta")));
    }

    private static BranchDto branch(UUID id, String name) {
        return new BranchDto(id, "BR-" + name, name, "1 Flower St", "District 1", "HCMC", "0280000000",
                new BigDecimal("10.776900"), new BigDecimal("106.700900"), new BigDecimal("15.00"),
                "08:00-20:00", "ACTIVE", FROM, FROM);
    }

    private static OrderDetailDto order(UUID branchId, String fulfilment, String status, String lineTotal, String total) {
        return new OrderDetailDto(UUID.randomUUID(), "ORD-1", UUID.randomUUID(), branchId, fulfilment, status, "PAID",
                lineTotal, "0.00", "0.00", total, "VND", "Recipient", "0901234567", null, null, null,
                List.of(new OrderLineSnapshotDto(UUID.randomUUID(), "SKU", "Bouquet", lineTotal, 1, lineTotal)),
                FROM.plusSeconds(60), FROM.plusSeconds(60));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void headOfficeGetsTheContractShapeWithSnapshotTotalsPerBranch() throws Exception {
        givenActiveBranches();
        when(orderApi.findOrdersForRevenueReport(Set.of(BRANCH_A, BRANCH_B), FROM, TO)).thenReturn(List.of(
                order(BRANCH_A, "DELIVERY", "COMPLETED", "500000.00", "535000.00"),
                order(BRANCH_A, "PICKUP", "CANCELLED", "700000.00", "700000.00"),
                order(BRANCH_B, "PICKUP", "COMPLETED", "300000.00", "300000.00")));

        mvc.perform(get(URL).param("from", FROM.toString()).param("to", TO.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(HEAD_OFFICE)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("2026-11-01T00:00:00Z"))
                .andExpect(jsonPath("$.to").value("2026-12-01T00:00:00Z"))
                .andExpect(jsonPath("$.currencyCode").value("VND"))
                .andExpect(jsonPath("$.totalChainRevenue").value("800000.00"))
                .andExpect(jsonPath("$.totalChainOrders").value(2))
                .andExpect(jsonPath("$.branches.length()").value(2))
                .andExpect(jsonPath("$.branches[0].branchId").value(BRANCH_A.toString()))
                .andExpect(jsonPath("$.branches[0].branchName").value("Alpha"))
                .andExpect(jsonPath("$.branches[0].completedOrdersCount").value(1))
                .andExpect(jsonPath("$.branches[0].deliveryOrdersCount").value(1))
                .andExpect(jsonPath("$.branches[0].pickupOrdersCount").value(0))
                .andExpect(jsonPath("$.branches[0].grossRevenue").value("500000.00"))
                .andExpect(jsonPath("$.branches[1].branchName").value("Beta"))
                .andExpect(jsonPath("$.branches[1].grossRevenue").value("300000.00"));
    }

    @Test
    void branchManagerWithoutAFilterOnlyGetsTheirBranch() throws Exception {
        givenActiveBranches();
        when(orderApi.findOrdersForRevenueReport(Set.of(BRANCH_A), FROM, TO))
                .thenReturn(List.of(order(BRANCH_A, "PICKUP", "COMPLETED", "250000.00", "250000.00")));

        mvc.perform(get(URL).param("from", FROM.toString()).param("to", TO.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branches.length()").value(1))
                .andExpect(jsonPath("$.branches[0].branchId").value(BRANCH_A.toString()))
                .andExpect(jsonPath("$.totalChainRevenue").value("250000.00"));

        verify(orderApi).findOrdersForRevenueReport(Set.of(BRANCH_A), FROM, TO);
    }

    @Test
    void branchManagerWithAForgedBranchFilterIsDenied() throws Exception {
        givenActiveBranches();

        mvc.perform(get(URL).param("branchId", BRANCH_B.toString())
                        .param("from", FROM.toString()).param("to", TO.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_A)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("BRANCH_ACCESS_DENIED"));
        verifyNoInteractions(orderApi);
    }

    @Test
    void rolesWithoutRevenueAccessAreDenied() throws Exception {
        for (String token : List.of(SALES_A, INVENTORY_A)) {
            mvc.perform(get(URL).param("from", FROM.toString()).param("to", TO.toString())
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_FORBIDDEN_ROLE"));
        }
        verifyNoInteractions(orderApi);
    }

    @Test
    void unauthenticatedCallGets401ProblemJson() throws Exception {
        mvc.perform(get(URL).param("from", FROM.toString()).param("to", TO.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("AUTH_UNAUTHORIZED"));
        verifyNoInteractions(orderApi, branchApi);
    }

    @Test
    void fromNotBeforeToIsA400ProblemDetail() throws Exception {
        for (String[] range : new String[][] {{TO.toString(), FROM.toString()}, {FROM.toString(), FROM.toString()}}) {
            mvc.perform(get(URL).param("from", range[0]).param("to", range[1])
                            .header(HttpHeaders.AUTHORIZATION, bearer(HEAD_OFFICE)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_DATE_RANGE"));
        }
        verifyNoInteractions(orderApi, branchApi);
    }

    @Test
    void missingOrMalformedParametersAre400ProblemDetails() throws Exception {
        mvc.perform(get(URL).param("from", FROM.toString()).header(HttpHeaders.AUTHORIZATION, bearer(HEAD_OFFICE)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("MISSING_REQUIRED_PARAMETER"));
        mvc.perform(get(URL).param("from", "last-monday").param("to", TO.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(HEAD_OFFICE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER_FORMAT"));
        mvc.perform(get(URL).param("branchId", "not-a-uuid").param("from", FROM.toString()).param("to", TO.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(HEAD_OFFICE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER_FORMAT"));
        verifyNoInteractions(orderApi, branchApi);
    }

    @Test
    void timestampsWithAnOffsetAreNormalisedToUtc() throws Exception {
        givenActiveBranches();
        when(orderApi.findOrdersForRevenueReport(any(), any(), any())).thenReturn(List.of());

        mvc.perform(get(URL).param("from", "2026-11-01T00:00:00+07:00").param("to", "2026-12-01T00:00:00+07:00")
                        .header(HttpHeaders.AUTHORIZATION, bearer(HEAD_OFFICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-10-31T17:00:00Z"));

        verify(orderApi).findOrdersForRevenueReport(
                Set.of(BRANCH_A, BRANCH_B),
                Instant.parse("2026-10-31T17:00:00Z"),
                Instant.parse("2026-11-30T17:00:00Z"));
    }
}
