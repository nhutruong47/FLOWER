package com.flowerchain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * TASK-018 defect 6: Boot's default in-memory user (and the generated password it logs at startup) is switched
 * off by excluding {@link UserDetailsServiceAutoConfiguration}. The full-context check runs in
 * {@code ApplicationSecurityWiringIT} (CI only).
 */
class FlowerChainApplicationTest {

    @Test
    void applicationExcludesTheDefaultUserDetailsServiceAutoConfiguration() {
        EnableAutoConfiguration enableAutoConfiguration =
                AnnotatedElementUtils.findMergedAnnotation(FlowerChainApplication.class, EnableAutoConfiguration.class);

        assertThat(enableAutoConfiguration).isNotNull();
        assertThat(List.of(enableAutoConfiguration.exclude())).contains(UserDetailsServiceAutoConfiguration.class);
    }

    @Test
    void theExcludedAutoConfigurationIsTheOneThatCreatesTheGeneratedUser() {
        // Guards the exclusion above against a Boot rename: this class, when active, creates the in-memory user.
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class))
                // A fixed password keeps this throwaway context from logging a generated one.
                .withPropertyValues("spring.security.user.password=test-only-not-a-secret")
                .run(context -> assertThat(context).hasSingleBean(UserDetailsService.class));

        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(UserDetailsService.class));
    }
}
