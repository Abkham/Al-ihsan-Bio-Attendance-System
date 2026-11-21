package com.developers_group.biometricattendance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Lightweight CSRF defence for a cookie-session JSON API.
 *
 * Every state-changing request to /api/**, /webauthn/** and /login/webauthn must carry the header
 * {@code X-Requested-With: XMLHttpRequest}. A web page on another site cannot
 * add a custom header to a cross-site request without a CORS pre-flight, and
 * CORS only allows the configured school origins. Together with
 * SameSite=Lax session cookies this blocks cross-site request forgery.
 */
public class ApiRequestHeaderFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean protectedPath = path.startsWith("/api/") || path.startsWith("/webauthn/") || path.equals("/login/webauthn");
        return !protectedPath || SAFE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        if (!"XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            JsonErrors.write(response, HttpServletResponse.SC_FORBIDDEN,
                    "This request was rejected for security reasons. Please use the school attendance web page.");
            return;
        }

        chain.doFilter(request, response);
    }
}
