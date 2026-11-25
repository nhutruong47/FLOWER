package com.flowerchain.shared.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TokenAuthenticationFilter tokenAuthenticationFilter;

    public SecurityConfig(TokenAuthenticationFilter tokenAuthenticationFilter) {
        this.tokenAuthenticationFilter = tokenAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/storefront/branches").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/storefront/categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/storefront/branches/*/products").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/storefront/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/storefront/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/payments/*").permitAll()
                        .requestMatchers("/api/v1/storefront/**").authenticated()
                        .requestMatchers("/api/v1/admin/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"type\":\"https://flowerchain.com/problems/unauthorized\","
                                            + "\"title\":\"Unauthorized\",\"status\":401,"
                                            + "\"detail\":\"Authentication is required to access this resource.\","
                                            + "\"errorCode\":\"AUTH_UNAUTHORIZED\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"type\":\"https://flowerchain.com/problems/forbidden-role\","
                                            + "\"title\":\"Access denied\",\"status\":403,"
                                            + "\"detail\":\"You do not have permission to access this resource.\","
                                            + "\"errorCode\":\"AUTH_FORBIDDEN_ROLE\"}");
                        }))
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
