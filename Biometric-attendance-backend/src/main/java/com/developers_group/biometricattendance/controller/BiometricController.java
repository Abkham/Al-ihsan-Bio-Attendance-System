package com.developers_group.biometricattendance.controller;

import com.developers_group.biometricattendance.dto.DeviceDTO;
import com.developers_group.biometricattendance.dto.DeviceRegistrationRequest;
import com.developers_group.biometricattendance.dto.RevokeDeviceRequest;
import com.developers_group.biometricattendance.entity.Biometric;
import com.developers_group.biometricattendance.repository.BiometricRepository;
import com.developers_group.biometricattendance.security.AccessGuard;
import com.developers_group.biometricattendance.security.AuthNames;
import com.developers_group.biometricattendance.service.DeviceService;
import com.developers_group.biometricattendance.service.EmployeeService;
import com.developers_group.biometricattendance.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Registered attendance devices.
 *
 *   employee: record + list + rename + remove (with password) THEIR OWN device
 *   admin   : list everyone's devices, unlock an account for a new device
 */
@RestController
public class BiometricController {

    private final DeviceService deviceService;
    private final BiometricRepository biometricRepository;
    private final AccessGuard accessGuard;

    public BiometricController(
            DeviceService deviceService,
            BiometricRepository biometricRepository,
            AccessGuard accessGuard) {

        this.deviceService = deviceService;
        this.biometricRepository = biometricRepository;
        this.accessGuard = accessGuard;
    }

    // Called by the browser AFTER the real WebAuthn registration succeeded.
    // 409 "This account's device fingerprint is already registered." for a second device.
    @PostMapping("/api/biometrics/employee/{employeeId}")
    public ResponseEntity<DeviceDTO> record(
            @PathVariable String employeeId,
            @RequestBody DeviceRegistrationRequest body,
            Authentication authentication) {

        accessGuard.requireSelfEmployee(authentication, employeeId);

        return new ResponseEntity<>(
                deviceService.recordRegistration(EmployeeService.normalizeId(employeeId), body),
                HttpStatus.CREATED);
    }

    @GetMapping("/api/biometrics/employee/{employeeId}")
    public ResponseEntity<List<DeviceDTO>> listForEmployee(
            @PathVariable String employeeId,
            Authentication authentication) {

        accessGuard.requireSelfOrAdmin(authentication, employeeId);

        return ResponseEntity.ok(deviceService.listForEmployee(EmployeeService.normalizeId(employeeId)));
    }

    // Admin overview (employee list shows "Registered / Not registered")
    @GetMapping("/api/biometrics")
    public ResponseEntity<List<DeviceDTO>> listAll() {
        return ResponseEntity.ok(deviceService.listAllDevices());
    }

    // Employee: give the device a friendly name (display only)
    @PatchMapping("/api/devices/employee/{employeeId}/nickname")
    public ResponseEntity<DeviceDTO> rename(
            @PathVariable String employeeId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {

        accessGuard.requireSelfEmployee(authentication, employeeId);

        return ResponseEntity.ok(deviceService.rename(
                EmployeeService.normalizeId(employeeId), body.get("deviceNickname")));
    }

    // ONLY the administrator can remove (unlock) an employee's device, for a lost or replaced phone.
    // An employee - even one who knows the password - cannot remove it, so nobody can swap in their own
    // fingerprint on someone else's account.
    @PostMapping("/api/devices/employee/{employeeId}/revoke")
    public ResponseEntity<Map<String, String>> revoke(
            @PathVariable String employeeId,
            @RequestBody(required = false) RevokeDeviceRequest body,
            Authentication authentication) {

        if (!AuthNames.isAdmin(authentication)) {
            throw ApiException.forbidden(
                    "Only the school administrator can remove a device. Please contact the administrator.");
        }

        String id = EmployeeService.normalizeId(employeeId);
        String reason = body == null ? null : body.getReason();

        deviceService.revokeByAdmin(id, "admin#" + AuthNames.adminId(authentication), reason);

        return ResponseEntity.ok(Map.of(
                "message", "The device was removed. A new device can now be registered."));
    }
}
