package com.flowerchain.shared.security;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** HTTP methods the REST API uses. Preflight {@code OPTIONS} requests are answered by the CORS filter itself. */
    static final List<String> CORS_ALLOWED_METHODS = List.of("GET", "POST", "PUT");

    /** Request headers browsers may send cross-origin. */
    static final List<String> CORS_ALLOWED_HEADERS = List.of("Authorization", "Content-Type", "Idempotency-Key");

    private final ObjectProvider<TokenPrincipalResolver> tokenPrincipalResolvers;

    public SecurityConfig(ObjectProvider<TokenPrincipalResolver> tokenPrincipalResolvers) {
        this.tokenPrincipalResolvers = tokenPrincipalResolvers;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * CORS for the storefront and admin apps: only the exact origins listed in
     * {@code flowerchain.security.cors-allowed-origins} (comma-separated) may call {@code /api/**} from a browser.
     * Wildcards are rejected at startup. Credentials (cookies) are not allowed because the API authenticates with
     * bearer tokens in the {@code Authorization} header.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${flowerchain.security.cors-allowed-origins:}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(parseAllowedOrigins(allowedOrigins));
        config.setAllowedMethods(CORS_ALLOWED_METHODS);
        config.setAllowedHeaders(CORS_ALLOWED_HEADERS);
        config.setAllowCredentials(false);
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
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
                // Created here rather than injected as a bean, so the filter runs only inside this chain and is
                // never also registered as a servlet filter by Spring Boot (TASK-018).
                .addFilterBefore(
                        new TokenAuthenticationFilter(tokenPrincipalResolvers),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Parses the comma-separated origin list. Each entry must be an exact {@code http} or {@code https} origin
     * ({@code scheme://host[:port]}); wildcards, paths, queries and the {@code null} origin are rejected so a
     * misconfiguration fails at startup instead of silently opening the API to other sites.
     */
    static List<String> parseAllowedOrigins(String property) {
        if (property == null || property.isBlank()) {
            return List.of();
        }
        return Arrays.stream(property.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .map(SecurityConfig::validateOrigin)
                .distinct()
                .toList();
    }

    private static String validateOrigin(String origin) {
        if (origin.contains("*")) {
            throw new IllegalStateException(
                    "flowerchain.security.cors-allowed-origins must list exact origins; wildcard '" + origin
                            + "' is not allowed.");
        }
        String normalized = origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "flowerchain.security.cors-allowed-origins contains an invalid origin '" + origin + "'.", e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean exactOrigin = ("http".equals(scheme) || "https".equals(scheme))
                && uri.getHost() != null
                && uri.getUserInfo() == null
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
        if (!exactOrigin) {
            throw new IllegalStateException(
                    "flowerchain.security.cors-allowed-origins entry '" + origin
                            + "' must be an origin of the form http(s)://host[:port].");
        }
        return normalized;
    }
}
