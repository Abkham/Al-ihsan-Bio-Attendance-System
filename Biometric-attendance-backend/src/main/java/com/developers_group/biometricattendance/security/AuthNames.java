package com.developers_group.biometricattendance.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** Naming conventions for the authenticated user stored in the HTTP session. */
public final class AuthNames {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_EMPLOYEE = "ROLE_EMPLOYEE";

    private static final String ADMIN_PREFIX = "ADMIN#";

    private AuthNames() {
    }

    /**
     * Administrators are identified by their numeric id, not their username,
     * so changing the username does not invalidate the current session.
     */
    public static String adminName(Long adminId) {
        return ADMIN_PREFIX + adminId;
    }

    public static boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || !authentication.isAuthenticated()) return false;

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (role.equals(authority.getAuthority())) return true;
        }

        return false;
    }

    public static boolean isAdmin(Authentication authentication) {
        return hasRole(authentication, ROLE_ADMIN);
    }

    public static boolean isEmployee(Authentication authentication) {
        return hasRole(authentication, ROLE_EMPLOYEE);
    }

    /** @return the administrator id, or null when the authentication is not an administrator. */
    public static Long adminId(Authentication authentication) {
        if (!isAdmin(authentication)) return null;

        String name = authentication.getName();

        if (name == null || !name.startsWith(ADMIN_PREFIX)) return null;

        try {
            return Long.parseLong(name.substring(ADMIN_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
