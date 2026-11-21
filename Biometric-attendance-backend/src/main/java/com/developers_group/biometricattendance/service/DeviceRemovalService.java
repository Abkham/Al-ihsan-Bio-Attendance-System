package com.developers_group.biometricattendance.service;

import com.developers_group.biometricattendance.dto.DeviceRemovalRequestDTO;
import com.developers_group.biometricattendance.entity.Biometric;
import com.developers_group.biometricattendance.entity.DeviceRemovalRequest;
import com.developers_group.biometricattendance.entity.Employee;
import com.developers_group.biometricattendance.exception.ApiException;
import com.developers_group.biometricattendance.repository.BiometricRepository;
import com.developers_group.biometricattendance.repository.DeviceRemovalRequestRepository;
import com.developers_group.biometricattendance.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controlled device removal. An employee can only ASK; the administrator decides.
 *
 *  NORMAL    PENDING -> APPROVED (device revoked, employee notified) | REJECTED (reason shown)
 *            | CANCELLED (by the employee)      ;  APPROVED -> COMPLETED when the new device is registered
 *  EMERGENCY (stolen / lost) PENDING -> APPROVED by "revoke immediately" (device revoked at once and a
 *            verification code is issued for the new registration) | REJECTED (false report)
 *
 * Every step runs in ONE transaction with the employee row locked, so a double click or two
 * simultaneous decisions cannot both succeed.
 */
@Service
public class DeviceRemovalService {

    static final Set<String> NORMAL_REASONS = Set.of("NEW_PHONE", "DAMAGED_DEVICE", "BIOMETRIC_PROBLEM", "OTHER");
    static final Set<String> EMERGENCY_REASONS = Set.of("STOLEN", "LOST");

    private static final Map<String, String> REASON_TEXT = Map.of(
            "NEW_PHONE", "I bought a new phone",
            "DAMAGED_DEVICE", "My device is damaged",
            "BIOMETRIC_PROBLEM", "Fingerprint / biometric problems",
            "OTHER", "Other reason",
            "STOLEN", "My phone was stolen",
            "LOST", "My phone is lost");

    private final DeviceRemovalRequestRepository requestRepository;
    private final EmployeeRepository employeeRepository;
    private final BiometricRepository biometricRepository;
    private final DeviceService deviceService;
    private final AuditService auditService;

    public DeviceRemovalService(
            DeviceRemovalRequestRepository requestRepository,
            EmployeeRepository employeeRepository,
            BiometricRepository biometricRepository,
            DeviceService deviceService,
            AuditService auditService) {

        this.requestRepository = requestRepository;
        this.employeeRepository = employeeRepository;
        this.biometricRepository = biometricRepository;
        this.deviceService = deviceService;
        this.auditService = auditService;
    }

    public static String reasonText(String code) {
        return REASON_TEXT.getOrDefault(code, code);
    }

    // ------------------------------------------------------------ employee

    @Transactional
    public DeviceRemovalRequestDTO submit(String employeeId, String type, String reason, String description) {

        boolean emergency = DeviceRemovalRequest.EMERGENCY.equals(type);

        if (!emergency && !DeviceRemovalRequest.NORMAL.equals(type)) {
            throw ApiException.badRequest("Unknown request type.");
        }

        Set<String> allowed = emergency ? EMERGENCY_REASONS : NORMAL_REASONS;

        if (reason == null || !allowed.contains(reason)) {
            throw ApiException.badRequest("Please choose a reason from the list.");
        }

        String text = DeviceService.clean(description, 500);

        if ("OTHER".equals(reason) && (text == null || text.length() < 5)) {
            throw ApiException.badRequest("Please explain your reason (at least a few words).");
        }

        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> ApiException.notFound("Employee not found"));

        Biometric device = biometricRepository
                .findByEmployeeEmployeeIdAndStatus(employee.getEmployeeId(), Biometric.ACTIVE)
                .stream().findFirst()
                .orElseThrow(() -> ApiException.conflict("You have no active registered device to remove."));

        String openKey = employee.getEmployeeId() + ":" + type;

        if (requestRepository.existsByOpenKey(openKey)) {
            throw ApiException.conflict(emergency
                    ? "You already reported this emergency. The administrator has been notified."
                    : "You already have a pending removal request. Please wait for the administrator's decision.");
        }

        DeviceRemovalRequest request = new DeviceRemovalRequest();
        request.setEmployee(employee);
        request.setDevice(device);
        request.setRequestType(type);
        request.setReason(reason);
        request.setDescription(text);
        request.setStatus(DeviceRemovalRequest.PENDING);
        request.setOpenKey(openKey);
        request.setRequestedAt(Instant.now());

        DeviceRemovalRequest saved = requestRepository.saveAndFlush(request);

        auditService.log(employee.getEmployeeId(), "EMPLOYEE",
                emergency ? "EMERGENCY_REPORT_SUBMITTED" : "REMOVAL_REQUEST_SUBMITTED",
                employee.getEmployeeId(), device.getBiometricId(), reasonText(reason));

        auditService.notify(employee, "REQUEST",
                emergency ? "Emergency report received" : "Removal request submitted",
                emergency
                        ? "The administrator has been notified. Please contact the school administrator in person "
                        + "or by phone to confirm your identity. Your device stays active until the administrator revokes it."
                        : "Your request is PENDING. You will be notified when the administrator decides.");

        return new DeviceRemovalRequestDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<DeviceRemovalRequestDTO> listForEmployee(String employeeId) {
        return requestRepository.findByEmployeeEmployeeIdOrderByRequestedAtDesc(employeeId).stream()
                .map(DeviceRemovalRequestDTO::new).toList();
    }

    @Transactional
    public DeviceRemovalRequestDTO cancel(String employeeId, Long requestId) {

        // Lock order everywhere: the request first, then the employee.
        DeviceRemovalRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> ApiException.notFound("Request not found"));

        // Someone else's request looks exactly like a missing one.
        if (!request.getEmployee().getEmployeeId().equals(employeeId)) {
            throw ApiException.notFound("Request not found");
        }

        employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> ApiException.notFound("Employee not found"));

        requirePending(request);

        close(request, DeviceRemovalRequest.CANCELLED, "employee", null);

        auditService.log(employeeId, "EMPLOYEE", "REMOVAL_REQUEST_CANCELLED", employeeId,
                request.getDevice() == null ? null : request.getDevice().getBiometricId(), "Cancelled by the employee.");

        return new DeviceRemovalRequestDTO(requestRepository.save(request));
    }

    // ------------------------------------------------------------ administrator

    @Transactional(readOnly = true)
    public List<DeviceRemovalRequestDTO> listAll() {
        return requestRepository.findAllByOrderByRequestedAtDesc().stream()
                .map(DeviceRemovalRequestDTO::new).toList();
    }

    @Transactional
    public DeviceRemovalRequestDTO approve(Long requestId, String adminLabel, String comment) {

        DeviceRemovalRequest request = lockAndLoad(requestId);

        if (!DeviceRemovalRequest.NORMAL.equals(request.getRequestType())) {
            throw ApiException.badRequest("An emergency report is handled with \"Revoke immediately\".");
        }

        Employee employee = request.getEmployee();

        deviceService.revokeForRequest(employee, adminLabel, "Removal request approved: " + reasonText(request.getReason()));

        close(request, DeviceRemovalRequest.APPROVED, adminLabel, comment);
        cancelOtherPending(employee, request, adminLabel);

        auditService.log(adminLabel, "ADMIN", "REMOVAL_REQUEST_APPROVED", employee.getEmployeeId(),
                request.getDevice() == null ? null : request.getDevice().getBiometricId(), reasonText(request.getReason()));

        auditService.notify(employee, "DEVICE_REVOKED", "Your old device was revoked",
                "Your removal request was approved and your old device has been revoked. "
                        + "You can now register your replacement device in Settings > Device."
                        + (request.getAdminComment() == null ? "" : " Administrator's note: " + request.getAdminComment()));

        return new DeviceRemovalRequestDTO(requestRepository.save(request));
    }

    @Transactional
    public DeviceRemovalRequestDTO reject(Long requestId, String adminLabel, String comment) {

        String why = DeviceService.clean(comment, 500);

        if (why == null || why.length() < 3) {
            throw ApiException.badRequest("Please write the reason for rejecting the request.");
        }

        DeviceRemovalRequest request = lockAndLoad(requestId);

        close(request, DeviceRemovalRequest.REJECTED, adminLabel, why);

        Employee employee = request.getEmployee();

        auditService.log(adminLabel, "ADMIN", "REMOVAL_REQUEST_REJECTED", employee.getEmployeeId(),
                request.getDevice() == null ? null : request.getDevice().getBiometricId(), why);

        auditService.notify(employee, "REQUEST_REJECTED", "Your removal request was rejected",
                "Your device stays registered. Reason: " + why);

        return new DeviceRemovalRequestDTO(requestRepository.save(request));
    }

    /** Result of an emergency revocation: the request plus the one-time verification code. */
    public record EmergencyResult(DeviceRemovalRequestDTO request, String verificationCode) {
    }

    @Transactional
    public EmergencyResult emergencyRevoke(Long requestId, String adminLabel, String comment) {

        DeviceRemovalRequest request = lockAndLoad(requestId);

        if (!DeviceRemovalRequest.EMERGENCY.equals(request.getRequestType())) {
            throw ApiException.badRequest("Only an emergency report can be revoked immediately.");
        }

        Employee employee = request.getEmployee();

        deviceService.revokeForRequest(employee, adminLabel, "Emergency: " + reasonText(request.getReason()));

        String code = deviceService.issueRecoveryCode(employee);

        close(request, DeviceRemovalRequest.APPROVED, adminLabel, comment);
        cancelOtherPending(employee, request, adminLabel);

        auditService.log(adminLabel, "ADMIN", "EMERGENCY_REVOKED", employee.getEmployeeId(),
                request.getDevice() == null ? null : request.getDevice().getBiometricId(), reasonText(request.getReason()));

        auditService.notify(employee, "DEVICE_REVOKED", "Your device was revoked",
                "Following your report, your device has been revoked and can no longer be used for attendance. "
                        + "To register a new device, the administrator must first confirm your identity and give you "
                        + "a verification code.");

        return new EmergencyResult(new DeviceRemovalRequestDTO(requestRepository.save(request)), code);
    }

    /** Administrator issues a fresh verification code (for example when the first one expired). */
    @Transactional
    public String reissueRecoveryCode(String employeeId, String adminLabel) {

        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> ApiException.notFound("Employee not found"));

        if (!DeviceService.recoveryPending(employee)) {
            throw ApiException.conflict("This employee does not need a verification code.");
        }

        String code = deviceService.issueRecoveryCode(employee);

        auditService.log(adminLabel, "ADMIN", "RECOVERY_CODE_REISSUED", employeeId, null,
                "A new verification code was issued.");

        return code;
    }

    // ------------------------------------------------------------ helpers

    private DeviceRemovalRequest lockAndLoad(Long requestId) {

        // The request is locked and read FRESH first: a second decision waits here, then sees the
        // final status and is refused. Then the employee row is locked for the device change.
        DeviceRemovalRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> ApiException.notFound("Request not found"));

        employeeRepository.findByIdForUpdate(request.getEmployee().getEmployeeId())
                .orElseThrow(() -> ApiException.notFound("Employee not found"));

        requirePending(request);

        return request;
    }

    private static void requirePending(DeviceRemovalRequest request) {
        if (!DeviceRemovalRequest.PENDING.equals(request.getStatus())) {
            throw ApiException.conflict("This request was already handled (" + request.getStatus() + ").");
        }
    }

    private static void close(DeviceRemovalRequest request, String status, String by, String comment) {
        request.setStatus(status);
        request.setOpenKey(null);
        request.setReviewedBy(by);
        request.setReviewedAt(Instant.now());
        request.setAdminComment(DeviceService.clean(comment, 500));
    }

    private void cancelOtherPending(Employee employee, DeviceRemovalRequest keep, String adminLabel) {
        for (DeviceRemovalRequest other
                : requestRepository.findByEmployeeEmployeeIdAndStatus(employee.getEmployeeId(), DeviceRemovalRequest.PENDING)) {
            if (other.getId().equals(keep.getId())) continue;
            close(other, DeviceRemovalRequest.CANCELLED, adminLabel, "The device was already revoked.");
            requestRepository.save(other);
        }
    }
}
