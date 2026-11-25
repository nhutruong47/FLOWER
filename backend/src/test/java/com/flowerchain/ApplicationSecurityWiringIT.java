package com.flowerchain;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowerchain.shared.security.TokenAuthenticationFilter;
import com.flowerchain.support.PostgresIntegrationTestSupport;
import jakarta.servlet.Filter;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * TASK-018 defects 3, 5 and 6 checked in the real Spring Boot application context. CI only (Docker).
 */
class ApplicationSecurityWiringIT extends PostgresIntegrationTestSupport {

    @Autowired private ConfigurableApplicationContext context;

    @Test
    void bootDoesNotCreateTheDefaultInMemoryUser() {
        assertThat(context.getBeanNamesForType(UserDetailsService.class))
                .as("UserDetailsService beans (Boot's generated-password user)")
                .isEmpty();
    }

    @Test
    void tokenFilterRunsOnlyInsideTheSecurityFilterChain() {
        assertThat(context.getBeanNamesForType(TokenAuthenticationFilter.class)).isEmpty();

        ServletContextInitializerBeans servletInitializers = new ServletContextInitializerBeans(context.getBeanFactory());
        List<Object> servletFilters = StreamSupport.stream(servletInitializers.spliterator(), false)
                .filter(AbstractFilterRegistrationBean.class::isInstance)
                .map(registration -> (Object) ((AbstractFilterRegistrationBean<?>) registration).getFilter())
                .toList();
        assertThat(servletFilters).isNotEmpty().noneMatch(TokenAuthenticationFilter.class::isInstance);

        List<Filter> chainFilters = context.getBean(SecurityFilterChain.class).getFilters();
        assertThat(chainFilters).filteredOn(TokenAuthenticationFilter.class::isInstance).hasSize(1);
    }

    @Test
    void corsUsesTheConfiguredOrigins() {
        CorsConfiguration cors = context.getBean("corsConfigurationSource", CorsConfigurationSource.class)
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/admin/orders"));

        assertThat(cors).isNotNull();
        // Default of flowerchain.security.cors-allowed-origins in application.yml.
        assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:5173", "http://localhost:5174");
        assertThat(cors.getAllowedHeaders()).containsExactly("Authorization", "Content-Type", "Idempotency-Key");
        assertThat(cors.getAllowCredentials()).isFalse();
    }
}
