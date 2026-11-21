package com.developers_group.biometricattendance.controller;

import com.developers_group.biometricattendance.security.ClientAddress;
import com.developers_group.biometricattendance.service.EmployeeRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public endpoint (under /api/auth/**): an employee who was registered by the administrator
 * creates their own username and password with the activation code they were given.
 */
@RestController
@RequestMapping("/api/auth")
public class RegistrationController {

    private final EmployeeRegistrationService registrationService;

    public RegistrationController(EmployeeRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/employee-register")
    public ResponseEntity<Map<String, String>> register(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        registrationService.register(
                body.get("employeeId"),
                body.get("activationCode"),
                body.get("username"),
                body.get("password"),
                body.get("confirmPassword"),
                ClientAddress.of(request));

        return new ResponseEntity<>(
                Map.of("message", "Your account was created. You can now log in with your new username and password."),
                HttpStatus.CREATED);
    }
}
