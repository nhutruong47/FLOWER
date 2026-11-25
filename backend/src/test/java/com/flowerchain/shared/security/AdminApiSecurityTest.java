package com.flowerchain.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowerchain.branch.api.BranchApi;
import com.flowerchain.branch.api.BranchDto;
import com.flowerchain.inventory.application.InventoryService;
import com.flowerchain.inventory.web.InventoryController;
import com.flowerchain.order.application.OrderService;
import com.flowerchain.order.web.OrderController;
import com.flowerchain.shared.api.BranchAuthorizationGuard;
import com.flowerchain.shared.api.IdempotencyExecutor;
import com.flowerchain.shared.api.PageResponse;
import com.flowerchain.shared.web.GlobalExceptionHandler;
import com.flowerchain.support.TestPrincipals;
import com.flowerchain.support.TestTokenResolver;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * HTTP-level security of the admin API with the real {@link SecurityConfig}, {@link BranchAuthorizationGuard},
 * exception handler and controllers (services mocked).
 *
 * <ul>
 *   <li>TEST-013 / TEST-014 (REQ-011, REQ-012): branch-A staff are denied branch-B data, including with a forged
 *       {@code branchId} in the path, the query or the body, and list endpoints are narrowed to branch A.</li>
 *   <li>Unauthenticated admin calls get 401 {@code application/problem+json}.</li>
 *   <li>TASK-018 defect 3: the token filter runs only inside the security filter chain.</li>
 *   <li>TASK-018 defect 5: CORS allows only the configured origins, methods and headers.</li>
 * </ul>
 */
@SpringJUnitWebConfig(AdminApiSecurityTest.TestConfig.class)
@TestPropertySource(properties = "flowerchain.security.cors-allowed-origins=http://localhost:5173, http://localhost:5174")
class AdminApiSecurityTest {

    static final UUID BRANCH_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID BRANCH_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    static final UUID BRANCH_C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    static final String MANAGER_A = "staff_manager_a";
    static final String INVENTORY_A = "staff_inventory_a";
    static final String SALES_A = "staff_sales_a";
    static final String HEAD_OFFICE = "staff_head_office";
    static final String CUSTOMER = "cust_customer";

    @Configuration
    @EnableWebMvc
    // Scans the security package like the application does, so a filter annotated as a bean would show up here.
    @ComponentScan(basePackageClasses = SecurityConfig.class)
    @Import({
        BranchAuthorizationGuard.class,
        GlobalExceptionHandler.class,
        InventoryController.class,
        OrderController.class
    })
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-25T08:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        TestTokenResolver testTokenResolver() {
            return new TestTokenResolver()
                    .register(MANAGER_A, TestPrincipals.staff("BRANCH_MANAGER", BRANCH_A))
                    .register(INVENTORY_A, TestPrincipals.staff("INVENTORY_STAFF", BRANCH_A))
                    .register(SALES_A, TestPrincipals.staff("SALES_STAFF", BRANCH_A))
                    .register(HEAD_OFFICE, TestPrincipals.staff("HEAD_OFFICE_ADMIN"))
                    .register(CUSTOMER, TestPrincipals.customer());
        }
    }

    @MockitoBean private InventoryService inventoryService;
    @MockitoBean private OrderService orderService;
    @MockitoBean private BranchApi branchApi;
    @MockitoBean private IdempotencyExecutor idempotencyExecutor;

    @Autowired private WebApplicationContext context;
    @Autowired private TestTokenResolver tokenResolver;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        tokenResolver.resetLookups();
        when(branchApi.listActiveBranches()).thenReturn(List.of(branch(BRANCH_A), branch(BRANCH_B), branch(BRANCH_C)));
    }

    private static BranchDto branch(UUID id) {
        return new BranchDto(id, "BR-" + id.toString().substring(35), "Branch " + id.toString().substring(35),
                "1 Flower St", "District 1", "HCMC", "0280000000", new BigDecimal("10.776900"),
                new BigDecimal("106.700900"), new BigDecimal("15.00"), "08:00-20:00", "ACTIVE",
                Instant.EPOCH, Instant.EPOCH);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Nested
    class Authentication {

        @Test
        void unauthenticatedAdminCallGets401ProblemJson() throws Exception {
            mvc.perform(get("/api/v1/admin/orders"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.errorCode").value("AUTH_UNAUTHORIZED"));
            verifyNoInteractions(orderService);
        }

        @Test
        void unknownBearerTokenGets401() throws Exception {
            mvc.perform(get("/api/v1/admin/branches/{branchId}/inventory", BRANCH_A)
                            .header(HttpHeaders.AUTHORIZATION, bearer("staff_forged_token")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            verifyNoInteractions(inventoryService);
        }

        @Test
        void basicAuthenticationIsNotAccepted() throws Exception {
            mvc.perform(post("/api/v1/admin/stock-transfers")
                            .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNzd29yZA==")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(inventoryService);
        }

        @Test
        void customerTokenCannotUseAdminEndpoints() throws Exception {
            mvc.perform(get("/api/v1/admin/orders").header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            verifyNoInteractions(orderService);
        }

        @Test
        void unmappedPathsAreDeniedByDefault() throws Exception {
            mvc.perform(get("/internal/anything").header(HttpHeaders.AUTHORIZATION, bearer(HEAD_OFFICE)))
                    .andExpect(status().isForbidden());
        }
    }

    /** TEST-013 / TEST-014: branch-A staff against branch-B data. */
    @Nested
    class BranchIsolation {

        @Test
        void forgedBranchIdInThePathIsDeniedForReads() throws Exception {
            mvc.perform(get("/api/v1/admin/branches/{branchId}/inventory", BRANCH_B)
                            .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_A)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value("BRANCH_ACCESS_DENIED"));
            mvc.perform(get("/api/v1/admin/branches/{branchId}/inventory/movements", BRANCH_B)
                            .header(HttpHeaders.AUTHORIZATION, bearer(INVENTORY_A)))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(inventoryService);
        }

        @Test
        void forgedBranchIdInThePathIsDeniedForStockChanges() throws Exception {
            UUID product = UUID.randomUUID();
            mvc.perform(post("/api/v1/admin/branches/{branchId}/goods-receipts", BRANCH_B)
                            .header(HttpHeaders.AUTHORIZATION, bearer(INVENTORY_A))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierName\":\"Da Lat Farm\",\"lines\":[{\"productId\":\"" + product
                                    + "\",\"receivedQuantity\":5}]}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("BRANCH_ACCESS_DENIED"));
            mvc.perform(post("/api/v1/admin/branches/{branchId}/stock-adjustments", BRANCH_B)
                            .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_A))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":\"" + product + "\",\"adjustmentType\":\"SPOILAGE\","
                                    + "\"quantityDelta\":-1,\"reasonNote\":\"forged write-off\"}"))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(inventoryService);
        }

        @Test
        void forgedBranchIdInTheQueryIsRejectedNotIgnored() throws Exception {
            mvc.perform(get("/api/v1/admin/orders").param("branchId", BRANCH_B.toString())
                            .header(HttpHeaders.AUTHORIZATION, bearer(SALES_A)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("BRANCH_ACCESS_DENIED"));
            mvc.perform(get("/api/v1/admin/stock-transfers").param("branchId", BRANCH_B.toString())
                            .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_A)))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(orderService, inventoryService);
        }

        @Test
        void forgedBranchIdsInTheBodyAreDenied() throws Exception {
            mvc.perform(post("/api/v1/admin/stock-transfers")
                            .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_A))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sourceBranchId\":\"" + BRANCH_B + "\",\"targetBranchId\":\"" + BRANCH_C
                                    + "\",\"lines\":[{\"productId\":\"" + UUID.randomUUID()
                                    + "\",\"requestedQuantity\":2}]}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("BRANCH_ACCESS_DENIED"));
            verifyNoInteractions(inventoryService);
        }

        @Test
        void listWithoutAFilterIsNarrowedToTheCallersBranch() throws Exception {
            when(orderService.listAdminOrders(any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(PageResponse.of(List.of(), 0, 20));

            mvc.perform(get("/api/v1/admin/orders").header(HttpHeaders.AUTHORIZATION, bearer(SALES_A)))
                    .andExpect(status().isOk());

            verify(orderService).listAdminOrders(eq(Set.of(BRANCH_A)), isNull(), isNull(), eq(0), eq(20));
        }

        @Test
        void ownBranchIsAllowed() throws Exception {
            when(inventoryService.listBranchInventory(BRANCH_A, 0, 20)).thenReturn(PageResponse.of(List.of(), 0, 20));

            mvc.perform(get("/api/v1/admin/branches/{branchId}/inventory", BRANCH_A)
                            .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_A)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void headOfficeMayReadAnyBranch() throws Exception {
            when(inventoryService.listBranchInventory(BRANCH_B, 0, 20)).thenReturn(PageResponse.of(List.of(), 0, 20));

            mvc.perform(get("/api/v1/admin/branches/{branchId}/inventory", BRANCH_B)
                            .header(HttpHeaders.AUTHORIZATION, bearer(HEAD_OFFICE)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class Validation {

        @Test
        void invalidListElementsAreStillValidatedAfterMovingValidOntoTheTypeArgument() throws Exception {
            mvc.perform(post("/api/v1/admin/stock-transfers")
                            .header(HttpHeaders.AUTHORIZATION, bearer(HEAD_OFFICE))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sourceBranchId\":\"" + BRANCH_A + "\",\"targetBranchId\":\"" + BRANCH_B
                                    + "\",\"lines\":[{\"productId\":null,\"requestedQuantity\":0}]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors['lines[0].requestedQuantity']").exists())
                    .andExpect(jsonPath("$.errors['lines[0].productId']").exists());
            verifyNoInteractions(inventoryService);
        }

        @Test
        void malformedJsonBodyIsA400ProblemNotA500() throws Exception {
            mvc.perform(post("/api/v1/admin/stock-transfers")
                            .header(HttpHeaders.AUTHORIZATION, bearer(HEAD_OFFICE))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST_BODY"));
        }
    }

    /** TASK-018 defect 3. */
    @Nested
    class TokenFilterRegistration {

        @Test
        void tokenFilterIsNotABeanSoSpringBootCannotRegisterItAsAServletFilter() {
            assertThat(context.getBeansOfType(TokenAuthenticationFilter.class)).isEmpty();

            ServletContextInitializerBeans servletInitializers = new ServletContextInitializerBeans(
                    ((ConfigurableApplicationContext) context).getBeanFactory());
            List<Filter> servletFilters = StreamSupport.stream(servletInitializers.spliterator(), false)
                    .filter(AbstractFilterRegistrationBean.class::isInstance)
                    .map(registration -> (Filter) ((AbstractFilterRegistrationBean<?>) registration).getFilter())
                    .toList();

            assertThat(servletFilters)
                    .as("filters Spring Boot would register with the servlet container")
                    .isNotEmpty()
                    .noneMatch(TokenAuthenticationFilter.class::isInstance);
        }

        @Test
        void tokenFilterRunsExactlyOnceInsideTheSecurityFilterChain() throws Exception {
            List<Filter> chainFilters = context.getBean(SecurityFilterChain.class).getFilters();
            assertThat(chainFilters).filteredOn(TokenAuthenticationFilter.class::isInstance).hasSize(1);

            when(inventoryService.listBranchInventory(BRANCH_A, 0, 20)).thenReturn(PageResponse.of(List.of(), 0, 20));
            mvc.perform(get("/api/v1/admin/branches/{branchId}/inventory", BRANCH_A)
                            .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_A)))
                    .andExpect(status().isOk());

            assertThat(tokenResolver.lookups()).as("token lookups for one request").isEqualTo(1);
        }
    }

    /** TASK-018 defect 5. */
    @Nested
    class Cors {

        @Test
        void preflightFromAConfiguredOriginIsAllowedWithTheApiHeaders() throws Exception {
            mvc.perform(options("/api/v1/admin/stock-transfers")
                            .header(HttpHeaders.ORIGIN, "http://localhost:5174")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type, Idempotency-Key"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5174"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Idempotency-Key")))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
            verifyNoInteractions(inventoryService);
        }

        @Test
        void preflightFromAnUnknownOriginIsRejected() throws Exception {
            mvc.perform(options("/api/v1/admin/stock-transfers")
                            .header(HttpHeaders.ORIGIN, "https://evil.example")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void preflightAskingForAnUnlistedHeaderOrMethodIsRejected() throws Exception {
            mvc.perform(options("/api/v1/admin/orders")
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Forwarded-Branch"))
                    .andExpect(status().isForbidden());
            mvc.perform(options("/api/v1/admin/orders")
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void actualRequestFromAnUnknownOriginIsRejectedEvenWithAValidToken() throws Exception {
            mvc.perform(get("/api/v1/admin/branches/{branchId}/inventory", BRANCH_A)
                            .header(HttpHeaders.ORIGIN, "https://evil.example")
                            .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_A)))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
            verifyNoInteractions(inventoryService);
        }

        @Test
        void actualRequestFromAConfiguredOriginGetsTheAllowOriginHeader() throws Exception {
            when(inventoryService.listBranchInventory(BRANCH_A, 0, 20)).thenReturn(PageResponse.of(List.of(), 0, 20));

            mvc.perform(get("/api/v1/admin/branches/{branchId}/inventory", BRANCH_A)
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_A)))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
        }
    }
}
