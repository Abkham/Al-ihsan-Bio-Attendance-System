package com.developers_group.biometricattendance.security;

import com.developers_group.biometricattendance.entity.Admin;
import com.developers_group.biometricattendance.entity.Employee;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Creates / replaces / destroys the server-side login session.
 *
 * Every successful login starts a brand-new session (the old one is
 * invalidated) which prevents session fixation AND gives the session a
 * creation time that SessionRevocationFilter compares with passwordChangedAt.
 */
@Service
public class SessionAuthService {

    private final SecurityContextRepository securityContextRepository;

    public SessionAuthService(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    public Authentication establishEmployee(
            Employee employee,
            HttpServletRequest request,
            HttpServletResponse response) {

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                employee.getUsername(),
                null,
                List.of(new SimpleGrantedAuthority(AuthNames.ROLE_EMPLOYEE)));

        establish(authentication, request, response);

        return authentication;
    }

    public Authentication establishAdmin(
            Admin admin,
            HttpServletRequest request,
            HttpServletResponse response) {

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                AuthNames.adminName(admin.getAdminId()),
                null,
                List.of(new SimpleGrantedAuthority(AuthNames.ROLE_ADMIN)));

        establish(authentication, request, response);

        return authentication;
    }

    /** Replaces the current session with a fresh one that carries the same identity. */
    public void renew(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {

        establish(authentication, request, response);
    }

    public void logout(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();
    }

    private void establish(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {

        HttpSession old = request.getSession(false);

        if (old != null) {
            old.invalidate();
        }

        request.getSession(true);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(context, request, response);
    }
}
