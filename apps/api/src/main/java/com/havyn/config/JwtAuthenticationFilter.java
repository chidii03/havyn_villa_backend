package com.havyn.config;

import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.auth.domain.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code Authorization: Bearer <access-token>}, and on a valid, unexpired token
 * populates the {@code SecurityContext} with an {@link AuthenticatedUser} principal +
 * {@code ROLE_*} authorities taken straight from the token's claims — no DB lookup per
 * request.
 *
 * <p><strong>Deliberately not a {@code @Component}.</strong> Spring Boot
 * auto-registers any {@code Filter} bean as a global servlet filter in addition to
 * wherever Spring Security places it, which would run this filter twice. Instead it's
 * constructed directly inside {@link SecurityConfig#filterChain} and wired in via
 * {@code addFilterBefore}, so it only ever runs once, inside the security chain.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            authenticate(header.substring(BEARER_PREFIX.length()));
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            JwtService.AccessClaims claims = jwtService.parseAccessToken(token);
            List<SimpleGrantedAuthority> authorities = claims.roles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            AuthenticatedUser principal = new AuthenticatedUser(claims.userId(), claims.email(), claims.roles());
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException invalidToken) {
            // Leave the SecurityContext empty — downstream authorization decides
            // 401 (no auth) vs 403 (wrong role) as appropriate, per endpoint.
            SecurityContextHolder.clearContext();
        }
    }
}
