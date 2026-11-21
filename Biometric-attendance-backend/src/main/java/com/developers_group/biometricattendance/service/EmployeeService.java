package com.developers_group.biometricattendance.service;

import com.developers_group.biometricattendance.entity.Employee;
import com.developers_group.biometricattendance.exception.ApiException;
import com.developers_group.biometricattendance.repository.AttendanceRepository;
import com.developers_group.biometricattendance.repository.DeviceRemovalRequestRepository;
import com.developers_group.biometricattendance.repository.EmployeeNotificationRepository;
import com.developers_group.biometricattendance.repository.EmployeeRepository;
import com.developers_group.biometricattendance.security.Codes;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.developers_group.biometricattendance.security.PasswordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final DeviceService deviceService;
    private final PasswordService passwordService;
    private final ProfilePhotoStorage photoStorage;
    private final PasswordEncoder passwordEncoder;
    private final DeviceRemovalRequestRepository removalRequestRepository;
    private final EmployeeNotificationRepository notificationRepository;
    private final AuditService auditService;

    /** How long an activation code stays valid. */
    static final int ACTIVATION_DAYS = 7;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            AttendanceRepository attendanceRepository,
            DeviceService deviceService,
            PasswordService passwordService,
            ProfilePhotoStorage photoStorage,
            PasswordEncoder passwordEncoder,
            DeviceRemovalRequestRepository removalRequestRepository,
            EmployeeNotificationRepository notificationRepository,
            AuditService auditService) {

        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.deviceService = deviceService;
        this.passwordService = passwordService;
        this.photoStorage = photoStorage;
        this.passwordEncoder = passwordEncoder;
        this.removalRequestRepository = removalRequestRepository;
        this.notificationRepository = notificationRepository;
        this.auditService = auditService;
    }

    // "emp004", " EMP004 " and "EMP004" are all stored/searched as "EMP004"
    public static String normalizeId(String employeeId) {

        if (employeeId == null || employeeId.trim().isEmpty()) {
            throw ApiException.badRequest("Employee ID is required");
        }

        return employeeId.trim().toUpperCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trimOrNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static boolean sameIgnoreCase(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    // =========================
    // CREATE EMPLOYEE (admin)
    // The photo is NOT part of this form any more: employees upload their own.
    // =========================

    /**
     * The administrator registers the employee's OFFICIAL details only. The employee creates their
     * own username and password later, proving who they are with the one-time activation code
     * returned here (shown to the administrator once; only its hash is stored).
     */
    @Transactional
    public Employee createEmployee(Employee employee, String adminLabel) {

        String id = normalizeId(employee.getEmployeeId());

        String first = NameRules.requiredName("First name", employee.getFirstName());
        String middle = NameRules.optionalName("Middle name", employee.getMiddleName());
        String last = NameRules.requiredName("Last name", employee.getLastName());
        String email = NameRules.optionalEmail(employee.getEmail());

        if (employeeRepository.existsById(id)) {
            throw ApiException.conflict("This Employee ID already exists");
        }

        if (email != null && employeeRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("This email is already in use");
        }

        Employee created = new Employee();
        created.setEmployeeId(id);
        created.setFirstName(first);
        created.setMiddleName(middle);
        created.setLastName(last);
        created.setEmail(email);
        created.setPhone(trimOrNull(employee.getPhone()));
        created.setDepartment(trimOrNull(employee.getDepartment()));
        created.setPosition(trimOrNull(employee.getPosition()));
        created.setHireDate(employee.getHireDate());
        created.setStatus("Inactive".equalsIgnoreCase(employee.getStatus()) ? "Inactive" : "Active");
        created.setProfilePhoto(null);

        // username / password stay empty until the employee registers.
        String code = Codes.generate();
        created.setActivationCodeHash(passwordEncoder.encode(Codes.forHashing(code)));
        created.setActivationCodeExpiresAt(Instant.now().plus(ACTIVATION_DAYS, ChronoUnit.DAYS));

        Employee saved = employeeRepository.save(created);

        auditService.log(adminLabel, "ADMIN", "EMPLOYEE_CREATED", id, null,
                "Employee registered; activation code issued.");

        saved.setActivationCode(code);

        return saved;
    }

    /** New activation code for an employee who has not created an account yet. */
    @Transactional
    public Employee regenerateActivationCode(String employeeId, String adminLabel) {

        Employee employee = employeeRepository.findByIdForUpdate(normalizeId(employeeId))
                .orElseThrow(() -> ApiException.notFound("Employee not found"));

        if (employee.isAccountRegistered()) {
            throw ApiException.conflict(
                    "This employee already created an account. Use Reset Password if they forgot it.");
        }

        String code = Codes.generate();
        employee.setActivationCodeHash(passwordEncoder.encode(Codes.forHashing(code)));
        employee.setActivationCodeExpiresAt(Instant.now().plus(ACTIVATION_DAYS, ChronoUnit.DAYS));

        Employee saved = employeeRepository.save(employee);

        auditService.log(adminLabel, "ADMIN", "ACTIVATION_CODE_REISSUED", employee.getEmployeeId(), null,
                "New activation code issued.");

        saved.setActivationCode(code);

        return saved;
    }

    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    public Optional<Employee> getEmployeeById(String employeeId) {
        return employeeRepository.findById(normalizeId(employeeId));
    }

    // =========================
    // UPDATE EMPLOYEE (admin). The Employee ID cannot change.
    // Photo is untouched here; password only changes when a new one is typed.
    // =========================

    @Transactional
    public Employee updateEmployee(String employeeId, Employee updated) {

        Employee existing = employeeRepository.findById(normalizeId(employeeId))
                .orElseThrow(() -> ApiException.notFound("Employee not found"));

        String oldUsername = existing.getUsername();

        if (existing.getUsername() == null && !isBlank(updated.getPassword())) {
            throw ApiException.badRequest(
                    "This employee has not created an account yet. Give them an activation code instead.");
        }

        if (!isBlank(updated.getUsername()) && existing.getUsername() != null) {

            String username = NameRules.username(updated.getUsername());

            if (!sameIgnoreCase(existing.getUsername(), username)
                    && employeeRepository.existsByUsernameIgnoreCase(username)) {
                throw ApiException.conflict("This username is already in use");
            }

            existing.setUsername(username);
        }

        if (!isBlank(updated.getEmail())) {

            String email = NameRules.optionalEmail(updated.getEmail());

            if (!sameIgnoreCase(existing.getEmail(), email)
                    && employeeRepository.existsByEmailIgnoreCase(email)) {
                throw ApiException.conflict("This email is already in use");
            }

            existing.setEmail(email);
        }

        if (!isBlank(updated.getFirstName())) {
            existing.setFirstName(NameRules.requiredName("First name", updated.getFirstName()));
        }

        // The edit form always sends the middle name: blank clears it.
        existing.setMiddleName(NameRules.optionalName("Middle name", updated.getMiddleName()));

        if (!isBlank(updated.getLastName())) {
            existing.setLastName(NameRules.requiredName("Last name", updated.getLastName()));
        }

        existing.setPhone(trimOrNull(updated.getPhone()));
        existing.setDepartment(trimOrNull(updated.getDepartment()));
        existing.setPosition(trimOrNull(updated.getPosition()));

        if (updated.getHireDate() != null) existing.setHireDate(updated.getHireDate());
        if (!isBlank(updated.getStatus())) {
            existing.setStatus("Inactive".equalsIgnoreCase(updated.getStatus()) ? "Inactive" : "Active");
        }

        if (!isBlank(updated.getPassword())) {
            existing.setPassword(passwordService.hashNew(updated.getPassword(), existing.getUsername()));
            existing.setPasswordChangedAt(now());   // signs the employee out everywhere
        }

        Employee saved = employeeRepository.save(existing);

        deviceService.renameWebAuthnUser(oldUsername, saved.getUsername());

        return saved;
    }

    @Transactional
    public Employee updateStatus(String employeeId, String status) {

        if (!"Active".equalsIgnoreCase(status) && !"Inactive".equalsIgnoreCase(status)) {
            throw ApiException.badRequest("Status must be Active or Inactive");
        }

        Employee employee = employeeRepository.findById(normalizeId(employeeId))
                .orElseThrow(() -> ApiException.notFound("Employee not found"));

        employee.setStatus("Inactive".equalsIgnoreCase(status) ? "Inactive" : "Active");

        return employeeRepository.save(employee);
    }

    @Transactional
    public void deleteEmployee(String employeeId) {

        String id = normalizeId(employeeId);

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Employee not found"));

        attendanceRepository.deleteByEmployeeEmployeeId(id);
        removalRequestRepository.deleteByEmployeeEmployeeId(id);
        notificationRepository.deleteByEmployeeEmployeeId(id);
        deviceService.deleteAllForEmployee(employee);
        employeeRepository.deleteById(id);

        photoStorage.deleteIfStoredAsFile(ProfilePhotoStorage.Kind.EMPLOYEE, employee.getProfilePhoto());
    }

    // =========================
    // EMPLOYEE'S OWN PHOTO
    // =========================

    @Transactional
    public Employee uploadProfilePhoto(String employeeId, MultipartFile photo) {

        Employee employee = employeeRepository.findById(normalizeId(employeeId))
                .orElseThrow(() -> ApiException.notFound("Employee not found"));

        String path = photoStorage.save(
                ProfilePhotoStorage.Kind.EMPLOYEE,
                employee.getEmployeeId(),
                photo,
                employee.getProfilePhoto());

        employee.setProfilePhoto(path);

        return employeeRepository.save(employee);
    }

    @Transactional
    public Employee removeProfilePhoto(String employeeId) {

        Employee employee = employeeRepository.findById(normalizeId(employeeId))
                .orElseThrow(() -> ApiException.notFound("Employee not found"));

        photoStorage.deleteIfStoredAsFile(ProfilePhotoStorage.Kind.EMPLOYEE, employee.getProfilePhoto());

        employee.setProfilePhoto(null);

        return employeeRepository.save(employee);
    }

    // =========================
    // CHANGE PASSWORD (self)
    // =========================

    @Transactional
    public void changePassword(String employeeId, String currentPassword, String newPassword) {

        Employee employee = employeeRepository.findById(normalizeId(employeeId))
                .orElseThrow(() -> ApiException.notFound("Employee not found"));

        if (!passwordService.matches(currentPassword, employee.getPassword())) {
            throw ApiException.badRequest("Current password is incorrect");
        }

        employee.setPassword(passwordService.hashNew(newPassword, employee.getUsername()));
        employee.setPasswordChangedAt(now());

        employeeRepository.save(employee);
    }
}
