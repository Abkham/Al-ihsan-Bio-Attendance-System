package com.developers_group.biometricattendance.controller;

import com.developers_group.biometricattendance.dto.DeviceRemovalRequestDTO;
import com.developers_group.biometricattendance.entity.AuditLog;
import com.developers_group.biometricattendance.entity.Employee;
import com.developers_group.biometricattendance.entity.EmployeeNotification;
import com.developers_group.biometricattendance.exception.ApiException;
import com.developers_group.biometricattendance.repository.EmployeeRepository;
import com.developers_group.biometricattendance.security.AccessGuard;
import com.developers_group.biometricattendance.security.AuthNames;
import com.developers_group.biometricattendance.security.FingerprintProof;
import com.developers_group.biometricattendance.security.LoginAttemptLimiter;
import com.developers_group.biometricattendance.security.RecoveryProof;
import com.developers_group.biometricattendance.service.AuditService;
import com.developers_group.biometricattendance.service.DeviceRemovalService;
import com.developers_group.biometricattendance.service.DeviceService;
import com.developers_group.biometricattendance.service.EmployeeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlled device removal, notifications and the audit trail.
 *
 *   employee : submit a request (normal: after a fingerprint check), see / cancel their own requests,
 *              read their notifications, enter the verification code after an emergency revocation
 *   admin    : see every request, approve / reject, revoke immediately (emergency), audit history
 * URL rules are in SecurityConfig; ownership is checked here.
 */
@RestController
public class DeviceRequestController {

    private final DeviceRemovalService removalService;
    private final AuditService auditService;
    private final DeviceService deviceService;
    private final EmployeeRepository employeeRepository;
    private final AccessGuard accessGuard;
    private final LoginAttemptLimiter limiter;

    public DeviceRequestController(
            DeviceRemovalService removalService,
            AuditService auditService,
            DeviceService deviceService,
            EmployeeRepository employeeRepository,
            AccessGuard accessGuard,
            @Qualifier("loginAttemptLimiter") LoginAttemptLimiter limiter) {

        this.removalService = removalService;
        this.auditService = auditService;
        this.deviceService = deviceService;
        this.employeeRepository = employeeRepository;
        this.accessGuard = accessGuard;
        this.limiter = limiter;
    }

    private static String adminLabel(Authentication authentication) {
        return "admin#" + AuthNames.adminId(authentication);
    }

    private static void requireAdmin(Authentication authentication) {
        if (!AuthNames.isAdmin(authentication)) {
            throw ApiException.forbidden("Only the school administrator can do this.");
        }
    }

    // ------------------------------------------------------------ employee

    @PostMapping("/api/device-requests/employee/{employeeId}")
    public ResponseEntity<DeviceRemovalRequestDTO> submit(
            @PathVariable String employeeId,
            @RequestBody Map<String, String> body,
            Authentication authentication,
            HttpServletRequest request) {

        accessGuard.requireSelfEmployee(authentication, employeeId);

        String type = body.get("requestType");

        // A normal request needs a fresh fingerprint check on the registered device: whoever only knows
        // the password cannot even ask. (An emergency exists precisely for when the phone is gone.)
        if ("NORMAL".equals(type)) {
            FingerprintProof.require(request, authentication.getName());
        }

        DeviceRemovalRequestDTO created = removalService.submit(
                EmployeeService.normalizeId(employeeId), type, body.get("reason"), body.get("description"));

        if ("NORMAL".equals(type)) FingerprintProof.clear(request);

        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/api/device-requests/employee/{employeeId}")
    public ResponseEntity<List<DeviceRemovalRequestDTO>> mine(
            @PathVariable String employeeId, Authentication authentication) {

        accessGuard.requireSelfEmployee(authentication, employeeId);

        return ResponseEntity.ok(removalService.listForEmployee(EmployeeService.normalizeId(employeeId)));
    }

    @PostMapping("/api/device-requests/employee/{employeeId}/{requestId}/cancel")
    public ResponseEntity<DeviceRemovalRequestDTO> cancel(
            @PathVariable String employeeId, @PathVariable Long requestId, Authentication authentication) {

        accessGuard.requireSelfEmployee(authentication, employeeId);

        return ResponseEntity.ok(removalService.cancel(EmployeeService.normalizeId(employeeId), requestId));
    }

    @GetMapping("/api/notifications/employee/{employeeId}")
    public ResponseEntity<Map<String, Object>> notifications(
            @PathVariable String employeeId, Authentication authentication) {

        accessGuard.requireSelfEmployee(authentication, employeeId);

        String id = EmployeeService.normalizeId(employeeId);

        List<Map<String, Object>> items = new java.util.ArrayList<>();

        for (EmployeeNotification n : auditService.notificationsFor(id)) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", n.getId());
            item.put("type", n.getType());
            item.put("title", n.getTitle());
            item.put("message", n.getMessage());
            item.put("createdAt", n.getCreatedAt());
            item.put("read", n.getReadAt() != null);
            items.add(item);
        }

        return ResponseEntity.ok(Map.of("unread", auditService.unreadCount(id), "items", items));
    }

    @PostMapping("/api/notifications/employee/{employeeId}/read-all")
    public ResponseEntity<Map<String, String>> readAll(
            @PathVariable String employeeId, Authentication authentication) {

        accessGuard.requireSelfEmployee(authentication, employeeId);

        auditService.markAllRead(EmployeeService.normalizeId(employeeId));

        return ResponseEntity.ok(Map.of("message", "OK"));
    }

    /** Does a new device need the administrator's verification code first? */
    @GetMapping("/api/devices/employee/{employeeId}/recovery-status")
    public ResponseEntity<Map<String, Object>> recoveryStatus(
            @PathVariable String employeeId, Authentication authentication, HttpServletRequest request) {

        accessGuard.requireSelfEmployee(authentication, employeeId);

        Employee employee = employeeRepository.findById(EmployeeService.normalizeId(employeeId))
                .orElseThrow(() -> ApiException.notFound("Employee not found"));

        return ResponseEntity.ok(Map.of(
                "required", DeviceService.recoveryPending(employee),
                "verified", RecoveryProof.isValid(request, authentication.getName())));
    }

    @PostMapping("/api/devices/employee/{employeeId}/verify-recovery")
    public ResponseEntity<Map<String, String>> verifyRecovery(
            @PathVariable String employeeId,
            @RequestBody Map<String, String> body,
            Authentication authentication,
            HttpServletRequest request) {

        accessGuard.requireSelfEmployee(authentication, employeeId);

        String id = EmployeeService.normalizeId(employeeId);
        String key = "rec:" + id.toLowerCase();

        if (limiter.isBlocked(key)) {
            throw ApiException.tooManyRequests("Too many wrong codes. Please wait 15 minutes or ask the administrator.");
        }

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Employee not found"));

        if (!DeviceService.recoveryPending(employee)) {
            throw ApiException.conflict("No verification code is needed for your account.");
        }

        if (!deviceService.recoveryCodeMatches(employee, body.get("code"))) {
            limiter.recordFailure(key);
            throw ApiException.forbidden(
                    "The verification code is not correct or has expired. Please ask the administrator.");
        }

        limiter.recordSuccess(key);
        RecoveryProof.mark(request, authentication.getName());

        return ResponseEntity.ok(Map.of("message", "Identity verified. You can now register your new device."));
    }

    // ------------------------------------------------------------ administrator

    @GetMapping("/api/device-requests")
    public ResponseEntity<List<DeviceRemovalRequestDTO>> all(Authentication authentication) {

        requireAdmin(authentication);

        return ResponseEntity.ok(removalService.listAll());
    }

    @PostMapping("/api/device-requests/{requestId}/approve")
    public ResponseEntity<DeviceRemovalRequestDTO> approve(
            @PathVariable Long requestId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {

        requireAdmin(authentication);

        return ResponseEntity.ok(removalService.approve(
                requestId, adminLabel(authentication), body == null ? null : body.get("comment")));
    }

    @PostMapping("/api/device-requests/{requestId}/reject")
    public ResponseEntity<DeviceRemovalRequestDTO> reject(
            @PathVariable Long requestId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {

        requireAdmin(authentication);

        return ResponseEntity.ok(removalService.reject(
                requestId, adminLabel(authentication), body == null ? null : body.get("comment")));
    }

    @PostMapping("/api/device-requests/{requestId}/emergency-revoke")
    public ResponseEntity<Map<String, Object>> emergencyRevoke(
            @PathVariable Long requestId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {

        requireAdmin(authentication);

        DeviceRemovalService.EmergencyResult result = removalService.emergencyRevoke(
                requestId, adminLabel(authentication), body == null ? null : body.get("comment"));

        Map<String, Object> out = new HashMap<>();
        out.put("request", result.request());
        out.put("verificationCode", result.verificationCode());
        out.put("message", "The device was revoked. Confirm the employee's identity, then give them the "
                + "verification code. It is shown only now.");

        return ResponseEntity.ok(out);
    }

    @PostMapping("/api/device-requests/recovery-code/{employeeId}")
    public ResponseEntity<Map<String, String>> newRecoveryCode(
            @PathVariable String employeeId, Authentication authentication) {

        requireAdmin(authentication);

        String code = removalService.reissueRecoveryCode(EmployeeService.normalizeId(employeeId), adminLabel(authentication));

        return ResponseEntity.ok(Map.of("verificationCode", code,
                "message", "Give this code to the employee after confirming their identity. It is shown only now."));
    }

    @GetMapping("/api/audit-logs")
    public ResponseEntity<List<AuditLog>> audit(
            @RequestParam(required = false) String employeeId, Authentication authentication) {

        requireAdmin(authentication);

        return ResponseEntity.ok(auditService.recent(employeeId));
    }
}
