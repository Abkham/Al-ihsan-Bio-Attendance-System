package com.developers_group.biometricattendance.controller;

import com.developers_group.biometricattendance.dto.ResetConfirmDTO;
import com.developers_group.biometricattendance.dto.ResetRequestDTO;
import com.developers_group.biometricattendance.security.AuthNames;
import com.developers_group.biometricattendance.security.ClientAddress;
import com.developers_group.biometricattendance.security.SessionAuthService;
import com.developers_group.biometricattendance.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Session + password-reset endpoints shared by employees and administrators. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final PasswordResetService resetService;
    private final SessionAuthService sessionAuthService;

    public AuthController(PasswordResetService resetService, SessionAuthService sessionAuthService) {
        this.resetService = resetService;
        this.sessionAuthService = sessionAuthService;
    }

    /** Tells the page whether its server session is still valid (and for which role). */
    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> session(Authentication authentication) {

        Map<String, Object> body = new LinkedHashMap<>();

        boolean signedIn = authentication != null && authentication.isAuthenticated()
                && (AuthNames.isAdmin(authentication) || AuthNames.isEmployee(authentication));

        body.put("authenticated", signedIn);
        body.put("role", !signedIn ? null : AuthNames.isAdmin(authentication) ? "ADMIN" : "EMPLOYEE");

        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        sessionAuthService.logout(request);
        return ResponseEntity.noContent().build();
    }

    // Always the same answer (see PasswordResetService).
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @RequestBody ResetRequestDTO body,
            HttpServletRequest request) {

        resetService.requestReset(body.getRole(), body.getUsername(), body.getEmail(),
                ClientAddress.of(request));

        return ResponseEntity.ok(Map.of("message", PasswordResetService.GENERIC_REQUEST_ANSWER));
    }

    @PostMapping("/reset-password/check")
    public ResponseEntity<Map<String, Object>> check(
            @RequestBody ResetConfirmDTO body,
            HttpServletRequest request) {

        return ResponseEntity.ok(Map.of(
                "valid", resetService.isTokenUsable(body.getToken(), ClientAddress.of(request))));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> reset(
            @RequestBody ResetConfirmDTO body,
            HttpServletRequest request) {

        resetService.confirmReset(
                body.getToken(), body.getNewPassword(), body.getConfirmPassword(),
                ClientAddress.of(request));

        return ResponseEntity.ok(Map.of(
                "message", "Your password has been changed. Please sign in with your new password."));
    }
}
