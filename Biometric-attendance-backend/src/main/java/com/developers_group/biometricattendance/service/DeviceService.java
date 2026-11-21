package com.developers_group.biometricattendance.service;

import com.developers_group.biometricattendance.dto.DeviceDTO;
import com.developers_group.biometricattendance.dto.DeviceRegistrationRequest;
import com.developers_group.biometricattendance.entity.Biometric;
import com.developers_group.biometricattendance.entity.DeviceRemovalRequest;
import com.developers_group.biometricattendance.entity.Employee;
import com.developers_group.biometricattendance.exception.ApiException;
import com.developers_group.biometricattendance.repository.BiometricRepository;
import com.developers_group.biometricattendance.repository.DeviceRemovalRequestRepository;
import com.developers_group.biometricattendance.repository.EmployeeRepository;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ONE ACTIVE ATTENDANCE DEVICE PER EMPLOYEE - enforced on the server.
 *
 * Layers (each one alone is enough to stop a second device):
 *  1. DeviceRegistrationGuardFilter refuses /webauthn/register[/options] when the
 *     account already has a credential (exact message below).
 *  2. UNIQUE(user_credentials.user_entity_user_id): the database itself refuses a
 *     second credential row for the same account, even for simultaneous requests.
 *  3. UNIQUE(biometric.active_key): at most one Active mirror row per employee.
 *  4. recordRegistration() locks the employee row (SELECT ... FOR UPDATE) so
 *     concurrent registrations are processed one after another.
 *
 * Replacement / recovery: revokeAll() deletes the old credential FIRST (so the lost
 * or replaced phone can no longer verify attendance) and only then can a new device
 * be registered. ONLY administrators can do this (for a lost or replaced phone).
 */
@Service
public class DeviceService {

    public static final String ALREADY_REGISTERED =
            "This account's device fingerprint is already registered.";

    private final JdbcOperations jdbc;
    private final BiometricRepository biometricRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final DeviceRemovalRequestRepository requestRepository;
    private final AuditService auditService;

    public DeviceService(
            JdbcOperations jdbc,
            BiometricRepository biometricRepository,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder,
            DeviceRemovalRequestRepository requestRepository,
            AuditService auditService) {

        this.jdbc = jdbc;
        this.biometricRepository = biometricRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.requestRepository = requestRepository;
        this.auditService = auditService;
    }

    // ---------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------

    /** Credential ids stored by Spring Security for this username (the real source of truth). */
    public List<String> credentialIdsForUsername(String username) {
        return jdbc.queryForList(
                "SELECT c.credential_id FROM user_credentials c "
                        + "JOIN user_entities u ON c.user_entity_user_id = u.id WHERE u.name = ?",
                String.class,
                username);
    }

    /** True when the account already has a registered device (real credential OR active mirror row). */
    public boolean hasRegisteredDevice(Employee employee) {
        return !credentialIdsForUsername(employee.getUsername()).isEmpty()
                || !biometricRepository
                .findByEmployeeEmployeeIdAndStatus(employee.getEmployeeId(), Biometric.ACTIVE).isEmpty();
    }

    @Transactional
    public List<DeviceDTO> listForEmployee(String employeeId) {

        syncMirrorWithCredentials(employeeId);

        return withLastUsed(biometricRepository.findByEmployeeEmployeeId(employeeId));
    }

    /** Administrator overview: every device (ACTIVE and REVOKED history) with its owner's name. */
    @Transactional(readOnly = true)
    public List<DeviceDTO> listAllDevices() {
        return withLastUsed(biometricRepository.findAll());
    }

    // Last time each credential was used = user_credentials.last_used (kept by Spring Security).
    private List<DeviceDTO> withLastUsed(List<Biometric> devices) {

        Map<String, java.time.Instant> lastUsed = new HashMap<>();

        for (Map<String, Object> row : jdbc.queryForList("SELECT credential_id, last_used FROM user_credentials")) {
            Object used = row.get("last_used");
            if (used instanceof Timestamp ts) lastUsed.put(String.valueOf(row.get("credential_id")), ts.toInstant());
        }

        return devices.stream().map(b -> {
            DeviceDTO dto = new DeviceDTO(b);
            dto.setLastUsedAt(lastUsed.get(b.getFingerprintId()));
            return dto;
        }).toList();
    }

    /**
     * Keeps the friendly device record (biometric table) in step with the REAL credential that
     * Spring Security stores in user_credentials, which is the source of truth:
     *
     *  - credential exists but no Active record (for example the record could not be saved right
     *    after registration): the record is created, using the device name stored with the
     *    credential, so the device shows up with its name instead of "already registered" and
     *    no device;
     *  - an Active record exists but the credential is gone (removed from the database by hand):
     *    the record is released, so the employee can register the device again.
     */
    private void syncMirrorWithCredentials(String employeeId) {

        Employee employee = employeeRepository.findByIdForUpdate(employeeId).orElse(null);

        if (employee == null) return;

        List<Map<String, Object>> credentials = jdbc.queryForList(
                "SELECT c.credential_id, c.label, c.created FROM user_credentials c "
                        + "JOIN user_entities u ON c.user_entity_user_id = u.id WHERE u.name = ?",
                employee.getUsername());

        List<Biometric> active =
                biometricRepository.findByEmployeeEmployeeIdAndStatus(employee.getEmployeeId(), Biometric.ACTIVE);

        if (credentials.isEmpty()) {

            Instant now = Instant.now();

            for (Biometric stale : active) {
                stale.setStatus(Biometric.REVOKED);
                stale.setActiveKey(null);
                stale.setRevokedAt(now);
                stale.setRevokedBy("SYSTEM");
                stale.setRevokeReason("The device credential no longer exists on the server.");
                biometricRepository.save(stale);
            }

            if (!active.isEmpty()) biometricRepository.flush();

            return;
        }

        if (!active.isEmpty()) return;

        Map<String, Object> row = credentials.get(0);

        Object credentialId = row.get("credential_id");

        if (credentialId == null) return;

        Object label = row.get("label");
        Object created = row.get("created");

        Biometric biometric = new Biometric();
        biometric.setEmployee(employee);
        biometric.setFingerprintId(String.valueOf(credentialId));
        biometric.setPublicKey("MANAGED_BY_SPRING_SECURITY_WEBAUTHN");
        biometric.setSignCount(0L);
        biometric.setStatus(Biometric.ACTIVE);
        biometric.setActiveKey(employee.getEmployeeId());
        biometric.setRegisteredAt(created instanceof java.sql.Timestamp ts ? ts.toInstant() : Instant.now());

        String name = label == null ? null : clean(String.valueOf(label), 60);
        biometric.setFingerName(name == null ? "Registered device" : name);

        biometricRepository.saveAndFlush(biometric);
    }

    // ---------------------------------------------------------------
    // Registration record (called AFTER the real WebAuthn ceremony succeeded)
    // ---------------------------------------------------------------

    @Transactional
    public DeviceDTO recordRegistration(String employeeId, DeviceRegistrationRequest request) {

        String credentialId = request == null ? null : request.getFingerprintId();

        if (credentialId == null || credentialId.isBlank()) {
            throw ApiException.badRequest("Biometric credential ID is required");
        }

        String deviceName = clean(request.getDeviceNickname(), 40);

        if (deviceName == null || deviceName.length() < 2) {
            throw ApiException.badRequest("Please enter a Device Name (for example: My Tecno Phone).");
        }

        // Row lock: simultaneous registrations for the same employee queue up here.
        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> ApiException.notFound("Employee not found"));

        // Never trust the browser: the credential must really exist on the server for THIS account.
        List<String> serverCredentials = credentialIdsForUsername(employee.getUsername());

        if (!serverCredentials.contains(credentialId)) {
            throw ApiException.badRequest(
                    "This device was not registered with the server. Please try the registration again.");
        }

        List<Biometric> active =
                biometricRepository.findByEmployeeEmployeeIdAndStatus(employee.getEmployeeId(), Biometric.ACTIVE);

        for (Biometric existing : active) {
            if (credentialId.equals(existing.getFingerprintId())) {
                // Same device reporting again (retry / double click): update the friendly details only.
                applyDeviceDetails(existing, request);
                return new DeviceDTO(biometricRepository.save(existing));
            }
        }

        if (!active.isEmpty()) {
            throw ApiException.conflict(ALREADY_REGISTERED);
        }

        Biometric biometric = new Biometric();
        biometric.setEmployee(employee);
        biometric.setFingerprintId(credentialId);
        biometric.setPublicKey("MANAGED_BY_SPRING_SECURITY_WEBAUTHN");
        biometric.setSignCount(0L);
        biometric.setStatus(Biometric.ACTIVE);
        biometric.setActiveKey(employee.getEmployeeId());
        biometric.setRegisteredAt(Instant.now());
        applyDeviceDetails(biometric, request);

        Biometric saved = biometricRepository.save(biometric);

        // The replacement is in place: close the approved removal request(s) and the recovery step.
        Instant now = Instant.now();

        for (DeviceRemovalRequest approved
                : requestRepository.findByEmployeeEmployeeIdAndStatus(employeeId, DeviceRemovalRequest.APPROVED)) {
            approved.setStatus(DeviceRemovalRequest.COMPLETED);
            approved.setCompletedAt(now);
            requestRepository.save(approved);
        }

        employee.setRecoveryCodeHash(null);
        employee.setRecoveryCodeExpiresAt(null);
        employeeRepository.save(employee);

        auditService.log(employee.getEmployeeId(), "EMPLOYEE", "DEVICE_REGISTERED", employee.getEmployeeId(),
                saved.getBiometricId(), "Registered device \"" + saved.getFingerName() + "\".");

        return new DeviceDTO(saved);
    }

    private void applyDeviceDetails(Biometric biometric, DeviceRegistrationRequest request) {

        biometric.setDeviceNickname(clean(request.getDeviceNickname(), 40));
        biometric.setDeviceModel(clean(request.getDeviceModel(), 60));
        biometric.setPlatform(clean(request.getPlatform(), 40));
        biometric.setBrowser(clean(request.getBrowser(), 40));
        biometric.setFingerName(displayName(biometric));
    }

    /**
     * Name shown to people. The employee's own nickname always wins; otherwise the
     * model reported by the browser; otherwise a generic "platform - browser" label.
     * This is DISPLAY ONLY and is never used for any security decision.
     */
    public static String displayName(Biometric b) {

        if (notBlank(b.getDeviceNickname())) {
            return b.getDeviceNickname();
        }

        String browser = notBlank(b.getBrowser()) ? b.getBrowser() : null;

        if (notBlank(b.getDeviceModel())) {
            return browser == null ? b.getDeviceModel() : b.getDeviceModel() + " (" + browser + ")";
        }

        String platform = notBlank(b.getPlatform()) ? b.getPlatform() : "Device";

        return browser == null ? platform : platform + " - " + browser;
    }

    // ---------------------------------------------------------------
    // Rename (display only)
    // ---------------------------------------------------------------

    @Transactional
    public DeviceDTO rename(String employeeId, String nickname) {

        Biometric active = biometricRepository
                .findByEmployeeEmployeeIdAndStatus(employeeId, Biometric.ACTIVE)
                .stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("No registered device found."));

        active.setDeviceNickname(clean(nickname, 40));
        active.setFingerName(displayName(active));

        return new DeviceDTO(biometricRepository.save(active));
    }

    // ---------------------------------------------------------------
    // Revocation / replacement
    // ---------------------------------------------------------------

    /** Administrator unlocks the account for a NEW device (lost / replaced phone). */
    @Transactional
    public void revokeByAdmin(String employeeId, String adminLabel, String reason) {

        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> ApiException.notFound("Employee not found"));

        revokeLocked(employee, "ADMIN:" + adminLabel, reason, true);
    }

    /**
     * Revocation on behalf of a removal request. The caller holds the employee row lock
     * (findByIdForUpdate) in the same transaction and finishes the request itself.
     */
    @Transactional
    public void revokeForRequest(Employee lockedEmployee, String adminLabel, String reason) {
        revokeLocked(lockedEmployee, "ADMIN:" + adminLabel, reason, false);
    }

    /** Used when an employee is deleted. */
    @Transactional
    public void deleteAllForEmployee(Employee employee) {

        deleteCredentials(employee.getUsername());
        biometricRepository.deleteByEmployeeEmployeeId(employee.getEmployeeId());
    }

    private void revokeLocked(Employee employee, String revokedBy, String reason, boolean cancelPending) {

        Long deviceId = biometricRepository
                .findByEmployeeEmployeeIdAndStatus(employee.getEmployeeId(), Biometric.ACTIVE)
                .stream().map(Biometric::getBiometricId).findFirst().orElse(null);

        // 1. The real credential goes first: the old phone can no longer verify attendance.
        deleteCredentials(employee.getUsername());

        // 2. Mark the mirror rows (history is kept).
        Instant now = Instant.now();

        for (Biometric b : biometricRepository.findByEmployeeEmployeeId(employee.getEmployeeId())) {
            if (!Biometric.REVOKED.equals(b.getStatus())) {
                b.setStatus(Biometric.REVOKED);
                b.setActiveKey(null);
                b.setRevokedAt(now);
                b.setRevokedBy(revokedBy);
                b.setRevokeReason(clean(reason, 400));
                biometricRepository.save(b);
            }
        }

        biometricRepository.flush();

        if (cancelPending) {
            for (DeviceRemovalRequest open
                    : requestRepository.findByEmployeeEmployeeIdAndStatus(employee.getEmployeeId(), DeviceRemovalRequest.PENDING)) {
                open.setStatus(DeviceRemovalRequest.CANCELLED);
                open.setOpenKey(null);
                open.setReviewedBy(revokedBy);
                open.setReviewedAt(now);
                open.setAdminComment("The administrator removed the device directly.");
                requestRepository.save(open);
            }
        }

        auditService.log(revokedBy, revokedBy != null && revokedBy.startsWith("ADMIN") ? "ADMIN" : "SYSTEM",
                "DEVICE_REVOKED", employee.getEmployeeId(), deviceId,
                "Device revoked" + (clean(reason, 200) == null ? "." : ": " + clean(reason, 200)));
    }

    // ---------------------------------------------------------------
    // Recovery (verification) code after an EMERGENCY revocation
    // ---------------------------------------------------------------

    public static boolean recoveryPending(Employee employee) {
        return employee.getRecoveryCodeHash() != null;
    }

    /** Creates a new verification code (valid 3 days). Returns the CLEAR code once; only a hash is stored. */
    public String issueRecoveryCode(Employee lockedEmployee) {

        String code = com.developers_group.biometricattendance.security.Codes.generate();

        lockedEmployee.setRecoveryCodeHash(
                passwordEncoder.encode(com.developers_group.biometricattendance.security.Codes.forHashing(code)));
        lockedEmployee.setRecoveryCodeExpiresAt(Instant.now().plus(3, ChronoUnit.DAYS));
        employeeRepository.save(lockedEmployee);

        return code;
    }

    /** @return true when the typed code is correct and not expired. The code stays valid until a device is registered. */
    public boolean recoveryCodeMatches(Employee employee, String typed) {

        if (employee.getRecoveryCodeHash() == null || employee.getRecoveryCodeExpiresAt() == null) return false;

        if (employee.getRecoveryCodeExpiresAt().isBefore(Instant.now())) return false;

        String normalized = com.developers_group.biometricattendance.security.Codes.normalize(typed);

        return !normalized.isEmpty() && passwordEncoder.matches(normalized, employee.getRecoveryCodeHash());
    }

    private void deleteCredentials(String username) {
        jdbc.update(
                "DELETE FROM user_credentials WHERE user_entity_user_id IN "
                        + "(SELECT id FROM user_entities WHERE name = ?)",
                username);
    }

    /** Keeps the WebAuthn account name in step when an administrator renames an employee's username. */
    public void renameWebAuthnUser(String oldUsername, String newUsername) {
        if (oldUsername == null || newUsername == null || oldUsername.equals(newUsername)) return;
        jdbc.update("UPDATE user_entities SET name = ? WHERE name = ?", newUsername, oldUsername);
    }

    // ---------------------------------------------------------------

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public static String clean(String value, int max) {

        if (value == null) return null;

        // strip control characters, collapse spaces, cap the length
        String cleaned = value.replaceAll("[\\p{Cntrl}<>]", "").replaceAll("\\s+", " ").trim();

        if (cleaned.isEmpty()) return null;

        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }
}
