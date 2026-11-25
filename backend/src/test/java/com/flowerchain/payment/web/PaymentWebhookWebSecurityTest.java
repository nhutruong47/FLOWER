package com.flowerchain.payment.web;

import static com.flowerchain.payment.WebhookTestSignatures.OTHER_SECRET;
import static com.flowerchain.payment.WebhookTestSignatures.SECRET;
import static com.flowerchain.payment.WebhookTestSignatures.payload;
import static com.flowerchain.payment.WebhookTestSignatures.sign;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowerchain.payment.application.PaymentRefundService;
import com.flowerchain.payment.application.PaymentWebhookEventProcessor;
import com.flowerchain.payment.application.PaymentWebhookService;
import com.flowerchain.payment.application.WebhookOutcome;
import com.flowerchain.payment.gateway.SandboxHmacPaymentGateway;
import com.flowerchain.payment.gateway.VerifiedPaymentEvent;
import com.flowerchain.shared.api.BranchAuthorizationGuard;
import com.flowerchain.shared.api.IdempotencyExecutor;
import com.flowerchain.shared.security.SecurityConfig;
import com.flowerchain.shared.security.TokenAuthenticationFilter;
import com.flowerchain.shared.web.GlobalExceptionHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Web + security slice (TEST-011, REQ-009): the real SecurityConfig, controller, webhook service, sandbox gateway
 * and exception handler, with the transactional processor mocked. Built with plain spring-test because Boot 4
 * moved {@code @WebMvcTest} into a module this project does not depend on.
 */
@SpringJUnitWebConfig(PaymentWebhookWebSecurityTest.SliceConfig.class)
class PaymentWebhookWebSecurityTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final String WEBHOOK_URL = "/api/v1/webhooks/payments/sandbox-hmac";

    @Configuration
    @EnableWebMvc
    @Import({
        SecurityConfig.class,
        TokenAuthenticationFilter.class,
        GlobalExceptionHandler.class,
        BranchAuthorizationGuard.class,
        PaymentWebhookController.class,
        AdminPaymentRefundController.class,
        PaymentWebhookService.class
    })
    static class SliceConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        SandboxHmacPaymentGateway sandboxHmacPaymentGateway(Clock clock, ObjectMapper objectMapper) {
            return new SandboxHmacPaymentGateway(SECRET, clock, objectMapper);
        }

        @Bean
        PaymentWebhookEventProcessor paymentWebhookEventProcessor() {
            return mock(PaymentWebhookEventProcessor.class);
        }

        @Bean
        PaymentRefundService paymentRefundService() {
            return mock(PaymentRefundService.class);
        }

        @Bean
        IdempotencyExecutor idempotencyExecutor() {
            return mock(IdempotencyExecutor.class);
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PaymentWebhookEventProcessor processor;

    @Autowired
    private PaymentRefundService refundService;

    @Autowired
    private IdempotencyExecutor idempotencyExecutor;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(processor, refundService, idempotencyExecutor);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void forgedSignatureWithoutTokenGets401ProblemFromTheVerifierAndNoProcessing() throws Exception {
        byte[] body = payload("evt_1001", "txn_9001", ORDER_ID, "CAPTURED", "500000", "VND");

        mvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SandboxHmacPaymentGateway.SIGNATURE_HEADER, sign(OTHER_SECRET, body))
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                // Our verifier answered (not the security entry point's AUTH_UNAUTHORIZED): the route is public.
                .andExpect(content().string(containsString("PAYMENT_WEBHOOK_SIGNATURE_INVALID")));

        verifyNoInteractions(processor);
    }

    @Test
    void missingSignatureGets401ProblemAndNoProcessing() throws Exception {
        byte[] body = payload("evt_1001", "txn_9001", ORDER_ID, "CAPTURED", "500000", "VND");

        mvc.perform(post(WEBHOOK_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(containsString("PAYMENT_WEBHOOK_SIGNATURE_INVALID")));

        verifyNoInteractions(processor);
    }

    @Test
    void tamperedBodyGets401ProblemAndNoProcessing() throws Exception {
        byte[] signedBody = payload("evt_1001", "txn_9001", ORDER_ID, "CAPTURED", "500000", "VND");
        byte[] sentBody = payload("evt_1001", "txn_9001", ORDER_ID, "CAPTURED", "1", "VND");

        mvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SandboxHmacPaymentGateway.SIGNATURE_HEADER, sign(SECRET, signedBody))
                        .content(sentBody))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(processor);
    }

    @Test
    void validSignatureWithoutTokenIsProcessedAndAcknowledged() throws Exception {
        byte[] body = payload("evt_1001", "txn_9001", ORDER_ID, "CAPTURED", "500000", "VND");
        when(processor.process(any(VerifiedPaymentEvent.class))).thenReturn(WebhookOutcome.PROCESSED);

        mvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SandboxHmacPaymentGateway.SIGNATURE_HEADER, sign(SECRET, body))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.eventId").value("evt_1001"))
                .andExpect(jsonPath("$.outcome").value("PROCESSED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vnpay", "momo"})
    void undecidedProvidersGet400ProblemAndNoProcessing(String provider) throws Exception {
        byte[] body = payload("evt_1001", "txn_9001", ORDER_ID, "CAPTURED", "500000", "VND");

        mvc.perform(post("/api/v1/webhooks/payments/" + provider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SandboxHmacPaymentGateway.SIGNATURE_HEADER, sign(SECRET, body))
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(containsString("PAYMENT_PROVIDER_NOT_SUPPORTED")));

        verifyNoInteractions(processor);
    }

    @Test
    void oversizedBodyGets413ProblemAndNoProcessing() throws Exception {
        byte[] body = new byte[PaymentWebhookController.MAX_BODY_BYTES + 1];

        mvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SandboxHmacPaymentGateway.SIGNATURE_HEADER, sign(SECRET, body))
                        .content(body))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(processor);
    }

    @Test
    void refundRouteRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/admin/payments/" + UUID.randomUUID() + "/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-1")
                        .content("{\"refundAmount\":\"1000\",\"reason\":\"Customer cancelled\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("AUTH_UNAUTHORIZED")));

        verifyNoInteractions(refundService, idempotencyExecutor);
    }
}
