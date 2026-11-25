package com.flowerchain.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.flowerchain.payment.application.PaymentWebhookEventProcessor;
import com.flowerchain.payment.application.PaymentWebhookService;
import com.flowerchain.shared.api.FlowerChainException;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Security review F2: the sandbox adapter exists only when explicitly enabled; otherwise it is unsupported. */
class SandboxHmacPaymentGatewayActivationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PaymentWiring.class);

    @Configuration(proxyBeanMethods = false)
    @Import({SandboxHmacPaymentGateway.class, PaymentWebhookService.class})
    static class PaymentWiring {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        PaymentWebhookEventProcessor paymentWebhookEventProcessor() {
            return mock(PaymentWebhookEventProcessor.class);
        }
    }

    @Test
    void adapterIsOffWhenThePropertyIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(SandboxHmacPaymentGateway.class);
            assertSandboxIsUnsupported(context.getBean(PaymentWebhookService.class));
        });
    }

    @Test
    void adapterIsOffWhenThePropertyIsFalse() {
        contextRunner.withPropertyValues(SandboxHmacPaymentGateway.ENABLED_PROPERTY + "=false").run(context -> {
            assertThat(context).doesNotHaveBean(SandboxHmacPaymentGateway.class);
            assertSandboxIsUnsupported(context.getBean(PaymentWebhookService.class));
        });
    }

    @Test
    void adapterIsOnOnlyWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues(
                        SandboxHmacPaymentGateway.ENABLED_PROPERTY + "=true",
                        SandboxHmacPaymentGateway.SECRET_PROPERTY + "=unit-and-it-test-webhook-secret-0123456789")
                .run(context -> {
                    assertThat(context).hasSingleBean(SandboxHmacPaymentGateway.class);
                    assertThat(context.getBean(PaymentWebhookService.class).resolveGateway("sandbox-hmac"))
                            .isSameAs(context.getBean(SandboxHmacPaymentGateway.class));
                });
    }

    private static void assertSandboxIsUnsupported(PaymentWebhookService service) {
        assertThatThrownBy(() -> service.resolveGateway("sandbox-hmac"))
                .isInstanceOfSatisfying(FlowerChainException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getErrorCode()).isEqualTo("PAYMENT_PROVIDER_NOT_SUPPORTED");
                });
    }
}
