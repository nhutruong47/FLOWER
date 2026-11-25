package com.flowerchain;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * FlowerChain modular monolith.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded: staff and customers authenticate with bearer tokens
 * resolved by {@code TokenAuthenticationFilter}, so Boot's default in-memory user (and the generated password it
 * logs at every startup) must not exist. Authorization rules in {@code SecurityConfig} are unchanged and still
 * deny by default (TASK-018).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class FlowerChainApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowerChainApplication.class, args);
    }

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }
}
