package com.havyn.config;

import com.havyn.auth.domain.JwtService;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Stateless JWT security: CSRF disabled (bearer tokens, not cookie sessions — the
 * refresh cookie is only ever read by /api/v1/auth/refresh, which doesn't need CSRF
 * protection since it doesn't rely on ambient cookie auth for anything sensitive), no
 * HTTP session, actuator health/info + OpenAPI docs + the auth endpoints themselves
 * are public. Everything else requires a valid access token; {@code @PreAuthorize} on
 * individual endpoints (see {@link EnableMethodSecurity}) adds role/object-level checks
 * on top as each module is built.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // Public browse/discovery + pricing-preview endpoints (prompts 10/11/12).
    // /api/v1/properties/** also covers POST /api/v1/properties/{id}/quote (prompt
    // 12) — a POST verb, but it persists nothing (pure price calculation), so it's
    // safe alongside the GET-only property/search endpoints under the same prefix.
    // Host-scoped listing management stays under /api/v1/host/listings, and booking
    // creation under /api/v1/bookings — neither is in this list; both require auth.
    // /api/v1/payments/webhook/** (prompt 13) must also be public — providers can't
    // authenticate with our bearer tokens; the endpoint verifies a provider-specific
    // signature itself instead. POST /api/v1/payments/intent stays authenticated
    // (guest-initiated, not in this list).
    //
    // POST /api/v1/properties/{id}/reviews (prompt 15) and POST
    // /api/v1/properties/{id}/conversations (prompt 16) are exceptions carved back out
    // of PUBLIC_PATHS below — both require auth to create (only an eligible guest may
    // review; only a real, non-host caller may start a conversation) even though GET
    // .../reviews and the rest of /api/v1/properties/** are public. These matchers are
    // registered before PUBLIC_PATHS so the more specific, method-scoped rules win.
    private static final String[] AUTH_REQUIRED_PROPERTY_POST_PATHS = {
            "/api/v1/properties/*/reviews", "/api/v1/properties/*/conversations"
    };

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
            "/api/v1/auth/**",
            "/api/v1/properties", "/api/v1/properties/**",
            "/api/v1/property-types",
            "/api/v1/amenities",
            "/api/v1/search",
            "/api/v1/payments/webhook/**"
    };

    // script-src/style-src need 'unsafe-inline' for springdoc's self-hosted Swagger UI
    // (/swagger-ui.html) — the only HTML this API ever serves; every other response is
    // JSON, which browsers never execute as a document (X-Content-Type-Options: nosniff
    // below is what actually matters there, not CSP). frame-ancestors 'none' still
    // blocks clickjacking-via-iframe on that HTML regardless.
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none'";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, AUTH_REQUIRED_PROPERTY_POST_PATHS).authenticated()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                // security/01-security-plan.md#platform: "Secure headers (HSTS,
                // X-Content-Type-Options, Referrer-Policy, CSP)". X-Content-Type-Options:
                // nosniff and X-Frame-Options: DENY are Spring Security defaults, made
                // explicit here rather than left implicit.
                .headers(headers -> headers
                        .contentTypeOptions(withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        // Only takes effect over HTTPS (browsers ignore it on plain HTTP,
                        // i.e. local dev) — real effect once a reverse proxy terminates TLS
                        // in front of this (prompts 27/29).
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(Duration.ofDays(365).toSeconds())));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // security/01-security-plan.md's documented target.
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
