package com.developers_group.biometricattendance.security;

import com.developers_group.biometricattendance.entity.Admin;
import com.developers_group.biometricattendance.entity.Employee;
import com.developers_group.biometricattendance.repository.AdminRepository;
import com.developers_group.biometricattendance.repository.EmployeeRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Rejects sessions that must no longer work:
 *  - the password was changed/reset AFTER the session was created
 *  - the account was deleted
 *  - the employee account was deactivated
 *
 * This is how a password reset "signs out everywhere".
 */
public class SessionRevocationFilter extends OncePerRequestFilter {

    private final EmployeeRepository employeeRepository;
    private final AdminRepository adminRepository;

    public SessionRevocationFilter(EmployeeRepository employeeRepository, AdminRepository adminRepository) {
        this.employeeRepository = employeeRepository;
        this.adminRepository = adminRepository;
    }

    /** True when a session created at {@code sessionCreatedMillis} is older than the last password change. */
    public static boolean createdBeforePasswordChange(long sessionCreatedMillis, Instant passwordChangedAt) {
        return passwordChangedAt != null
                && Instant.ofEpochMilli(sessionCreatedMillis).isBefore(passwordChangedAt);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {

            HttpSession session = request.getSession(false);
            long created = session == null ? 0L : session.getCreationTime();

            String problem = problemWith(authentication, created);

            if (problem != null) {

                SecurityContextHolder.clearContext();

                if (session != null) {
                    session.invalidate();
                }

                JsonErrors.write(response, HttpServletResponse.SC_UNAUTHORIZED, problem, "SESSION_REVOKED");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private String problemWith(Authentication authentication, long sessionCreatedMillis) {

        if (AuthNames.isAdmin(authentication)) {

            Long adminId = AuthNames.adminId(authentication);

            Admin admin = adminId == null ? null : adminRepository.findById(adminId).orElse(null);

            if (admin == null) {
                return "Your session is no longer valid. Please sign in again.";
            }

            if (createdBeforePasswordChange(sessionCreatedMillis, admin.getPasswordChangedAt())) {
                return "Your password was changed. Please sign in again.";
            }

            return null;
        }

        if (AuthNames.isEmployee(authentication)) {

            Employee employee = employeeRepository.findByUsernameIgnoreCase(authentication.getName()).orElse(null);

            if (employee == null) {
                return "Your session is no longer valid. Please sign in again.";
            }

            if ("Inactive".equalsIgnoreCase(employee.getStatus())) {
                return "This employee account is inactive. Please contact the administrator.";
            }

            if (createdBeforePasswordChange(sessionCreatedMillis, employee.getPasswordChangedAt())) {
                return "Your password was changed. Please sign in again.";
            }
        }

        return null;
    }
}
