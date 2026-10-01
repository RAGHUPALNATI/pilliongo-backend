package com.raghu.pilliongo.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            // A password-reset token (purpose=password-reset) is only ever
            // meant to be read by /api/auth/reset-password via
            // JwtUtil.validateResetToken() — it must never be accepted here
            // as a Bearer credential for ordinary authenticated endpoints.
            // Without this check, a leaked reset token could otherwise be
            // used to hit any "authenticated" (non-role-restricted) route.
            if (jwtUtil.validateToken(token) && !JwtUtil.PURPOSE_PASSWORD_RESET.equals(jwtUtil.extractPurpose(token))) {
                String email = jwtUtil.extractEmail(token);
                String role = jwtUtil.extractRole(token);

                var auth = new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}