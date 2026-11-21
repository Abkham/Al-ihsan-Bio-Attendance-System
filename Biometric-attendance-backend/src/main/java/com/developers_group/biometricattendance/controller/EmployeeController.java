package com.developers_group.biometricattendance.controller;

import com.developers_group.biometricattendance.dto.ChangePasswordDTO;
import com.developers_group.biometricattendance.dto.LoginRequestDTO;
import com.developers_group.biometricattendance.dto.StatusUpdateDTO;
import com.developers_group.biometricattendance.entity.Employee;
import com.developers_group.biometricattendance.security.AccessGuard;
import com.developers_group.biometricattendance.security.AuthNames;
import com.developers_group.biometricattendance.security.ClientAddress;
import com.developers_group.biometricattendance.security.SessionAuthService;
import com.developers_group.biometricattendance.service.EmployeeService;
import com.developers_group.biometricattendance.service.LoginService;
import com.developers_group.biometricattendance.service.WebAuthnUserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

// CORS is configured once for every /api/** URL in config/CorsConfig.java.
// Who may call what is decided in config/SecurityConfig.java + AccessGuard.
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final LoginService loginService;
    private final SessionAuthService sessionAuthService;
    private final WebAuthnUserAccountService webAuthnUserAccountService;
    private final AccessGuard accessGuard;

    public EmployeeController(
            EmployeeService employeeService,
            LoginService loginService,
            SessionAuthService sessionAuthService,
            WebAuthnUserAccountService webAuthnUserAccountService,
            AccessGuard accessGuard) {

        this.employeeService = employeeService;
        this.loginService = loginService;
        this.sessionAuthService = sessionAuthService;
        this.webAuthnUserAccountService = webAuthnUserAccountService;
        this.accessGuard = accessGuard;
    }

    // The response carries the one-time "activationCode" (shown to the administrator once).
    @PostMapping
    public ResponseEntity<Employee> createEmployee(@RequestBody Employee employee, Authentication authentication) {
        return new ResponseEntity<>(
                employeeService.createEmployee(employee, "admin#" + AuthNames.adminId(authentication)),
                HttpStatus.CREATED);
    }

    // New activation code for an employee who has not created an account yet (administrator only).
    @PostMapping("/{id}/activation-code")
    public ResponseEntity<Map<String, String>> newActivationCode(@PathVariable String id, Authentication authentication) {

        Employee employee = employeeService.regenerateActivationCode(id, "admin#" + AuthNames.adminId(authentication));

        return ResponseEntity.ok(Map.of(
                "employeeId", employee.getEmployeeId(),
                "activationCode", employee.getActivationCode(),
                "message", "Give this code to the employee. It works once and expires in 7 days."));
    }

    @GetMapping
    public ResponseEntity<List<Employee>> getAllEmployees() {
        return ResponseEntity.ok(employeeService.getAllEmployees());
    }

    // {id} is the string Employee ID, e.g. /api/employees/EMP004 (employees: only their own)
    @GetMapping("/{id}")
    public ResponseEntity<Employee> getEmployeeById(@PathVariable String id, Authentication authentication) {

        accessGuard.requireSelfOrAdmin(authentication, id);

        return employeeService.getEmployeeById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Employee> updateEmployee(@PathVariable String id, @RequestBody Employee employee) {
        return ResponseEntity.ok(employeeService.updateEmployee(id, employee));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Employee> updateStatus(@PathVariable String id, @RequestBody StatusUpdateDTO request) {
        return ResponseEntity.ok(employeeService.updateStatus(id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable String id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    // The employee's OWN photo
    @PostMapping("/{id}/profile-photo")
    public ResponseEntity<Employee> uploadProfilePhoto(
            @PathVariable String id,
            @RequestParam("photo") MultipartFile photo,
            Authentication authentication) {

        accessGuard.requireSelfEmployee(authentication, id);

        return ResponseEntity.ok(employeeService.uploadProfilePhoto(id, photo));
    }

    @DeleteMapping("/{id}/profile-photo")
    public ResponseEntity<Employee> removeProfilePhoto(@PathVariable String id, Authentication authentication) {

        accessGuard.requireSelfEmployee(authentication, id);

        return ResponseEntity.ok(employeeService.removeProfilePhoto(id));
    }

    @PostMapping("/login")
    public ResponseEntity<Employee> login(
            @RequestBody LoginRequestDTO request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        Employee employee = loginService.loginEmployee(
                request.getUsername(), request.getPassword(), ClientAddress.of(httpRequest));

        // brand-new session (anti session-fixation) carrying ROLE_EMPLOYEE
        sessionAuthService.establishEmployee(employee, httpRequest, httpResponse);

        // The WebAuthn account must exist (with a stable id) before the device screen is opened.
        try {
            webAuthnUserAccountService.ensureUserEntity(employee);
        } catch (Exception ex) {
            // Password login must NEVER fail because of the WebAuthn bridge.
            System.err.println("WebAuthn user entity could not be prepared: " + ex.getClass().getSimpleName());
        }

        return ResponseEntity.ok(employee);
    }

    @PostMapping("/{id}/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @PathVariable String id,
            @RequestBody ChangePasswordDTO request,
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        accessGuard.requireSelfEmployee(authentication, id);

        employeeService.changePassword(id, request.getCurrentPassword(), request.getNewPassword());

        // Other browsers are signed out; this one continues with a fresh session.
        sessionAuthService.renew(authentication, httpRequest, httpResponse);

        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
