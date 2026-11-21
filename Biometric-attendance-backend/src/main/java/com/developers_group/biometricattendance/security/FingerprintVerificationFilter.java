package com.developers_group.biometricattendance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Watches Spring Security's own WebAuthn sign-in endpoint (POST /login/webauthn).
 *
 * When that request SUCCEEDS (the fingerprint signature was verified against the stored
 * public key) and the signed-in user is an employee, it records a short-lived
 * {@link FingerprintProof} in the session. It never changes how the request is handled;
 * a failed or refused request leaves no proof behind.
 */
public class FingerprintVerificationFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/login/webauthn".equals(path(request)));
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        chain.doFilter(request, response);

        int status = response.getStatus();

        if (status < 200 || status >= 300) return;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (AuthNames.isEmployee(authentication)) {
            FingerprintProof.mark(request, authentication.getName());
        }
    }
}
