package com.developers_group.biometricattendance.controller;

import com.developers_group.biometricattendance.dto.ChangePasswordDTO;
import com.developers_group.biometricattendance.dto.LoginRequestDTO;
import com.developers_group.biometricattendance.entity.Admin;
import com.developers_group.biometricattendance.security.AccessGuard;
import com.developers_group.biometricattendance.security.ClientAddress;
import com.developers_group.biometricattendance.security.SessionAuthService;
import com.developers_group.biometricattendance.service.AdminService;
import com.developers_group.biometricattendance.service.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admins")
public class AdminController {

    private final AdminService adminService;
    private final LoginService loginService;
    private final SessionAuthService sessionAuthService;
    private final AccessGuard accessGuard;

    public AdminController(
            AdminService adminService,
            LoginService loginService,
            SessionAuthService sessionAuthService,
            AccessGuard accessGuard) {

        this.adminService = adminService;
        this.loginService = loginService;
        this.sessionAuthService = sessionAuthService;
        this.accessGuard = accessGuard;
    }

    @PostMapping
    public ResponseEntity<Admin> createAdmin(@RequestBody Admin admin) {
        return new ResponseEntity<>(adminService.createAdmin(admin), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<Admin>> getAllAdmins() {
        return ResponseEntity.ok(adminService.getAllAdmins());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Admin> getAdminById(@PathVariable Long id) {

        return adminService.getAdminById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Profile incl. USERNAME. An administrator edits only their own profile.
    @PutMapping("/{id}")
    public ResponseEntity<Admin> updateAdmin(
            @PathVariable Long id,
            @RequestBody Admin admin,
            Authentication authentication) {

        accessGuard.requireSelfAdmin(authentication, id);

        return ResponseEntity.ok(adminService.updateAdmin(id, admin));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAdmin(@PathVariable Long id, Authentication authentication) {

        // Nobody deletes the account they are signed in with.
        accessGuard.requireNotSelfAdmin(authentication, id);

        adminService.deleteAdmin(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<Admin> login(
            @RequestBody LoginRequestDTO request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        Admin admin = loginService.loginAdmin(
                request.getUsername(), request.getPassword(), ClientAddress.of(httpRequest));

        sessionAuthService.establishAdmin(admin, httpRequest, httpResponse);

        return ResponseEntity.ok(admin);
    }

    @PostMapping("/{id}/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @PathVariable Long id,
            @RequestBody ChangePasswordDTO request,
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        accessGuard.requireSelfAdmin(authentication, id);

        adminService.changePassword(id, request.getCurrentPassword(), request.getNewPassword());

        sessionAuthService.renew(authentication, httpRequest, httpResponse);

        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    @PostMapping("/{id}/profile-photo")
    public ResponseEntity<Admin> uploadProfilePhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        accessGuard.requireSelfAdmin(authentication, id);

        return ResponseEntity.ok(adminService.uploadProfilePhoto(id, file));
    }
}
