package com.flowerchain.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/** CORS configuration built from {@code flowerchain.security.cors-allowed-origins} (TASK-018 defect 5). */
class SecurityConfigTest {

    @SuppressWarnings("unchecked")
    private final SecurityConfig config = new SecurityConfig(mock(ObjectProvider.class));

    private static CorsConfiguration corsFor(CorsConfigurationSource source, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        return source.getCorsConfiguration(request);
    }

    @Test
    void commaSeparatedOriginsAreTrimmedDeduplicatedAndNormalised() {
        assertThat(SecurityConfig.parseAllowedOrigins(
                        " http://localhost:5173 , https://shop.example.vn/ ,,http://localhost:5173"))
                .containsExactly("http://localhost:5173", "https://shop.example.vn");
    }

    @Test
    void blankPropertyAllowsNoOrigin() {
        assertThat(SecurityConfig.parseAllowedOrigins(null)).isEmpty();
        assertThat(SecurityConfig.parseAllowedOrigins("  ")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "*",
        "https://*.example.vn",
        "http://localhost:5173,*",
        "localhost:5173",
        "ftp://files.example.vn",
        "https://shop.example.vn/admin",
        "https://shop.example.vn?x=1",
        "https://user@shop.example.vn",
        "null"
    })
    void wildcardsAndNonOriginValuesFailAtStartup(String property) {
        assertThatThrownBy(() -> SecurityConfig.parseAllowedOrigins(property))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("flowerchain.security.cors-allowed-origins");
    }

    @Test
    void apiPathsGetExactlyTheConfiguredOriginsMethodsAndHeadersWithoutCredentials() {
        CorsConfigurationSource source =
                config.corsConfigurationSource("http://localhost:5173,http://localhost:5174");

        CorsConfiguration cors = corsFor(source, "/api/v1/admin/orders");

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:5173", "http://localhost:5174");
        assertThat(cors.getAllowedOriginPatterns()).isNullOrEmpty();
        assertThat(cors.getAllowedMethods()).containsExactly("GET", "POST", "PUT");
        assertThat(cors.getAllowedHeaders()).containsExactly("Authorization", "Content-Type", "Idempotency-Key");
        assertThat(cors.getAllowCredentials()).isFalse();
        assertThat(cors.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
        assertThat(cors.checkOrigin("https://evil.example")).isNull();
    }

    @Test
    void nonApiPathsHaveNoCorsConfiguration() {
        CorsConfigurationSource source = config.corsConfigurationSource("http://localhost:5173");

        assertThat(corsFor(source, "/actuator/health")).isNull();
    }
}
