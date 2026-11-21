package com.developers_group.biometricattendance.service;

import com.developers_group.biometricattendance.entity.Admin;
import com.developers_group.biometricattendance.entity.Employee;
import com.developers_group.biometricattendance.exception.ApiException;
import com.developers_group.biometricattendance.repository.AdminRepository;
import com.developers_group.biometricattendance.repository.EmployeeRepository;
import com.developers_group.biometricattendance.security.LoginAttemptLimiter;
import com.developers_group.biometricattendance.security.PasswordService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Username/password verification for both roles (the login screen still chooses the role).
 * Wrong-password attempts are throttled per account AND per network address, and the
 * error text is the same whether the account exists or not.
 */
@Service
public class LoginService {

    private static final String EMPLOYEE_FAIL =
            "Login failed. Employee ID/Username or password is not recognized.";
    private static final String ADMIN_FAIL =
            "Login failed. Username or password is not recognized.";
    private static final String BLOCKED =
            "Too many failed attempts. Please wait 15 minutes and try again, or use Reset Password.";

    private final EmployeeRepository employeeRepository;
    private final AdminRepository adminRepository;
    private final PasswordService passwordService;
    private final LoginAttemptLimiter limiter;
    private final LoginAttemptLimiter addressLimiter;

    public LoginService(
            EmployeeRepository employeeRepository,
            AdminRepository adminRepository,
            PasswordService passwordService,
            @Qualifier("loginAttemptLimiter") LoginAttemptLimiter limiter,
            @Qualifier("addressLoginLimiter") LoginAttemptLimiter addressLimiter) {

        this.employeeRepository = employeeRepository;
        this.adminRepository = adminRepository;
        this.passwordService = passwordService;
        this.limiter = limiter;
        this.addressLimiter = addressLimiter;
    }

    public Employee loginEmployee(String identifier, String password, String clientAddress) {

        if (identifier == null || identifier.isBlank() || password == null || password.isEmpty()) {
            throw ApiException.badRequest("Please enter your Employee ID/username and password");
        }

        String entered = identifier.trim();
        String accountKey = "emp:" + entered.toLowerCase(Locale.ROOT);
        String addressKey = "addr:" + clientAddress;

        if (limiter.isBlocked(accountKey) || addressLimiter.isBlocked(addressKey)) {
            throw ApiException.tooManyRequests(BLOCKED);
        }

        Employee employee = employeeRepository.findByUsernameIgnoreCase(entered)
                .or(() -> employeeRepository.findById(entered.toUpperCase()))
                .orElse(null);

        if (employee == null) {
            passwordService.spendTimeLikeARealCheck(password);
            limiter.recordFailure(accountKey);
            addressLimiter.recordFailure(addressKey);
            throw ApiException.unauthorized(EMPLOYEE_FAIL);
        }

        if (!passwordService.matches(password, employee.getPassword())) {
            limiter.recordFailure(accountKey);
            addressLimiter.recordFailure(addressKey);
            throw ApiException.unauthorized(EMPLOYEE_FAIL);
        }

        limiter.recordSuccess(accountKey);

        if ("Inactive".equalsIgnoreCase(employee.getStatus())) {
            throw ApiException.forbidden(
                    "This employee account is inactive. Please contact the administrator.");
        }

        return employee;
    }

    public Admin loginAdmin(String username, String password, String clientAddress) {

        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw ApiException.badRequest("Please enter administrator credentials");
        }

        String entered = username.trim();
        String accountKey = "adm:" + entered.toLowerCase(Locale.ROOT);
        String addressKey = "addr:" + clientAddress;

        if (limiter.isBlocked(accountKey) || addressLimiter.isBlocked(addressKey)) {
            throw ApiException.tooManyRequests(BLOCKED);
        }

        Admin admin = adminRepository.findByUsernameIgnoreCase(entered).orElse(null);

        if (admin == null) {
            passwordService.spendTimeLikeARealCheck(password);
            limiter.recordFailure(accountKey);
            addressLimiter.recordFailure(addressKey);
            throw ApiException.unauthorized(ADMIN_FAIL);
        }

        if (!passwordService.matches(password, admin.getPassword())) {
            limiter.recordFailure(accountKey);
            addressLimiter.recordFailure(addressKey);
            throw ApiException.unauthorized(ADMIN_FAIL);
        }

        limiter.recordSuccess(accountKey);

        return admin;
    }

    /** Re-checks the signed-in employee's own password (used before removing a device, etc.). */
    public boolean confirmEmployeePassword(Employee employee, String password) {
        return passwordService.matches(password, employee.getPassword());
    }
}
