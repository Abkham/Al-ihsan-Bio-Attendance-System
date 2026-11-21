package com.developers_group.biometricattendance.service;

import com.developers_group.biometricattendance.entity.Employee;
import com.developers_group.biometricattendance.exception.ApiException;
import com.developers_group.biometricattendance.repository.EmployeeRepository;
import com.developers_group.biometricattendance.security.Codes;
import com.developers_group.biometricattendance.security.LoginAttemptLimiter;
import com.developers_group.biometricattendance.security.PasswordService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * An employee creates their OWN username and password.
 *
 * Employee IDs are not secret, so the ID alone proves nothing: the employee must also type the
 * one-time activation code the administrator gave them. Wrong attempts are throttled per Employee ID
 * and per network address, and every "not eligible" answer is worded the same so the form cannot be
 * used to find out which IDs exist.
 */
@Service
public class EmployeeRegistrationService {

    static final String NOT_ELIGIBLE =
            "The Employee ID or activation code is not correct, or the code has expired or was already used. "
                    + "Please check them or ask the administrator for a new activation code.";

    private static final String BLOCKED =
            "Too many failed attempts. Please wait 15 minutes and try again.";

    private final EmployeeRepository employeeRepository;
    private final PasswordService passwordService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final LoginAttemptLimiter limiter;
    private final LoginAttemptLimiter addressLimiter;

    public EmployeeRegistrationService(
            EmployeeRepository employeeRepository,
            PasswordService passwordService,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            @Qualifier("loginAttemptLimiter") LoginAttemptLimiter limiter,
            @Qualifier("addressLoginLimiter") LoginAttemptLimiter addressLimiter) {

        this.employeeRepository = employeeRepository;
        this.passwordService = passwordService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.limiter = limiter;
        this.addressLimiter = addressLimiter;
    }

    @Transactional
    public void register(
            String employeeId, String activationCode, String username,
            String password, String confirmPassword, String clientAddress) {

        if (employeeId == null || employeeId.isBlank()
                || activationCode == null || activationCode.isBlank()
                || username == null || username.isBlank()
                || password == null || password.isEmpty()) {
            throw ApiException.badRequest("Please fill in every field.");
        }

        String id = EmployeeService.normalizeId(employeeId);
        String accountKey = "reg:" + id.toLowerCase(Locale.ROOT);
        String addressKey = "addr:" + clientAddress;

        if (limiter.isBlocked(accountKey) || addressLimiter.isBlocked(addressKey)) {
            throw ApiException.tooManyRequests(BLOCKED);
        }

        Employee employee = employeeRepository.findByIdForUpdate(id).orElse(null);

        String normalizedCode = Codes.normalize(activationCode);

        boolean codeOk = employee != null
                && employee.getActivationCodeHash() != null
                && employee.getActivationCodeExpiresAt() != null
                && employee.getActivationCodeExpiresAt().isAfter(Instant.now())
                && passwordEncoder.matches(normalizedCode, employee.getActivationCodeHash());

        if (employee == null) {
            passwordService.spendTimeLikeARealCheck(normalizedCode);
        }

        if (!codeOk || employee.isAccountRegistered()) {
            limiter.recordFailure(accountKey);
            addressLimiter.recordFailure(addressKey);
            throw ApiException.forbidden(NOT_ELIGIBLE);
        }

        // From here on the person proved they hold the code; a typing mistake does not use it up.
        if ("Inactive".equalsIgnoreCase(employee.getStatus())) {
            throw ApiException.forbidden(
                    "This employee record is inactive. Please contact the administrator.");
        }

        if (!password.equals(confirmPassword)) {
            throw ApiException.badRequest("The two passwords do not match.");
        }

        String name = NameRules.username(username);

        if (employeeRepository.existsByUsernameIgnoreCase(name)) {
            throw ApiException.conflict("This username is already in use. Please choose another one.");
        }

        String hash = passwordService.hashNew(password, name);

        employee.setUsername(name);
        employee.setPassword(hash);
        employee.setPasswordChangedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        employee.setActivationCodeHash(null);          // single use
        employee.setActivationCodeExpiresAt(null);

        employeeRepository.saveAndFlush(employee);

        limiter.recordSuccess(accountKey);

        auditService.log(id, "EMPLOYEE", "ACCOUNT_REGISTERED", id, null, "Employee created their account.");
    }
}
