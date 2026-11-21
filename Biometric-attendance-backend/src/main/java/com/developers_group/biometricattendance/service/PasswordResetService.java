package com.developers_group.biometricattendance.service;

import com.developers_group.biometricattendance.entity.Admin;
import com.developers_group.biometricattendance.entity.Employee;
import com.developers_group.biometricattendance.entity.PasswordResetToken;
import com.developers_group.biometricattendance.exception.ApiException;
import com.developers_group.biometricattendance.repository.AdminRepository;
import com.developers_group.biometricattendance.repository.EmployeeRepository;
import com.developers_group.biometricattendance.repository.PasswordResetTokenRepository;
import com.developers_group.biometricattendance.security.LoginAttemptLimiter;
import com.developers_group.biometricattendance.security.PasswordService;
import com.developers_group.biometricattendance.security.ResetTokens;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Secure password reset (employees and administrators).
 *
 * 1. requestReset: username/ID + e-mail are only used to FIND the account. A random
 *    one-time token is e-mailed to the REGISTERED address - never to the typed one, never
 *    shown on screen. The answer is always the same, so nobody can discover which
 *    accounts exist.
 * 2. Knowing the username and the e-mail is NOT enough: the new password can only be set
 *    with the token that arrives in the registered mailbox.
 * 3. The token expires (default 30 minutes), works once (atomic UPDATE), and only its
 *    SHA-256 hash is stored.
 * 4. A successful reset stores a new BCrypt hash, sets passwordChangedAt (which makes the
 *    server reject every older session) and deletes all other reset tokens of the account.
 */
@Service
public class PasswordResetService {

    public static final String GENERIC_REQUEST_ANSWER =
            "If the details match a registered account with an e-mail address, "
                    + "a password reset link has been sent. Please check your inbox (and spam folder).";

    public static final String INVALID_LINK =
            "This reset link is invalid or has expired. Please request a new one.";

    private static final int MAX_REQUESTS_PER_HOUR_PER_ACCOUNT = 3;

    private final EmployeeRepository employeeRepository;
    private final AdminRepository adminRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordService passwordService;
    private final MailService mailService;
    private final LoginAttemptLimiter requestLimiter;
    private final LoginAttemptLimiter confirmLimiter;
    private final long tokenMinutes;
    private final String frontendUrl;

    public PasswordResetService(
            EmployeeRepository employeeRepository,
            AdminRepository adminRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordService passwordService,
            MailService mailService,
            @Qualifier("resetRequestLimiter") LoginAttemptLimiter requestLimiter,
            @Qualifier("resetConfirmLimiter") LoginAttemptLimiter confirmLimiter,
            @Value("${app.password-reset.token-minutes:30}") long tokenMinutes,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {

        this.employeeRepository = employeeRepository;
        this.adminRepository = adminRepository;
        this.tokenRepository = tokenRepository;
        this.passwordService = passwordService;
        this.mailService = mailService;
        this.requestLimiter = requestLimiter;
        this.confirmLimiter = confirmLimiter;
        this.tokenMinutes = tokenMinutes;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    // ---------------------------------------------------------------
    // 1. Request
    // ---------------------------------------------------------------

    /** @param role "ADMIN" or "EMPLOYEE" (the same role choice as the login screen) */
    @Transactional
    public void requestReset(String role, String identifier, String email, String clientAddress) {

        String idKey = "reset-id:" + (identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT));

        if (requestLimiter.isBlocked(idKey)) {
            throw ApiException.tooManyRequests("Too many reset requests. Please try again later.");
        }

        // Every request counts, so one account cannot be used to flood a mailbox.
        requestLimiter.recordFailure(idKey);

        if (identifier == null || identifier.isBlank() || email == null || email.isBlank()) {
            return;   // same generic answer as everything else
        }

        boolean admin = "ADMIN".equalsIgnoreCase(role);
        String entered = identifier.trim();
        String typedEmail = email.trim();

        String accountRef;
        String registeredEmail;
        String displayName;

        if (admin) {
            Admin account = adminRepository.findByUsernameIgnoreCase(entered).orElse(null);
            if (account == null) return;
            accountRef = String.valueOf(account.getAdminId());
            registeredEmail = account.getEmail();
            displayName = account.getFullName();
        } else {
            Employee account = employeeRepository.findByUsernameIgnoreCase(entered)
                    .or(() -> employeeRepository.findById(entered.toUpperCase()))
                    .orElse(null);
            if (account == null || "Inactive".equalsIgnoreCase(account.getStatus())) return;
            accountRef = account.getEmployeeId();
            registeredEmail = account.getEmail();
            displayName = account.getFullName();
        }

        // No registered e-mail, or the typed e-mail is not the registered one: do nothing.
        if (registeredEmail == null || registeredEmail.isBlank()
                || !registeredEmail.trim().equalsIgnoreCase(typedEmail)) {
            return;
        }

        String type = admin ? PasswordResetToken.ADMIN : PasswordResetToken.EMPLOYEE;
        Instant now = Instant.now();

        if (tokenRepository.countByAccountTypeAndAccountRefAndCreatedAtAfter(
                type, accountRef, now.minus(1, ChronoUnit.HOURS)) >= MAX_REQUESTS_PER_HOUR_PER_ACCOUNT) {
            return;
        }

        tokenRepository.deleteExpiredBefore(now.minus(1, ChronoUnit.DAYS));

        String token = ResetTokens.newToken();

        PasswordResetToken row = new PasswordResetToken();
        row.setAccountType(type);
        row.setAccountRef(accountRef);
        row.setTokenHash(ResetTokens.hash(token));
        row.setCreatedAt(now);
        row.setExpiresAt(now.plus(Duration.ofMinutes(tokenMinutes)));
        tokenRepository.save(row);

        // Administrators have their own address (/admin); employees use the main one.
        String link = frontendUrl + (admin ? "/admin/reset-password" : "/reset-password") + "?token=" + token;

        mailService.sendAsync(
                registeredEmail.trim(),
                "Reset your Al-Ihsan attendance password",
                "Hello" + (displayName == null || displayName.isBlank() ? "" : " " + displayName) + ",\n\n"
                        + "We received a request to reset the password of your Al-Ihsan Girls Secondary School "
                        + "attendance account.\n\n"
                        + "Open this link to choose a new password (valid for " + tokenMinutes
                        + " minutes, works once):\n" + link + "\n\n"
                        + "If you did not ask for this, ignore this message: your password has not changed.\n");
    }

    // ---------------------------------------------------------------
    // 2. Check a link (so the page can say "expired" before asking for a password)
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public boolean isTokenUsable(String token, String clientAddress) {

        String key = "reset-confirm:" + clientAddress;

        if (confirmLimiter.isBlocked(key)) {
            throw ApiException.tooManyRequests("Too many attempts. Please try again later.");
        }

        boolean ok = findUsable(token) != null;

        if (!ok) confirmLimiter.recordFailure(key);

        return ok;
    }

    private PasswordResetToken findUsable(String token) {

        if (!ResetTokens.looksValid(token)) return null;

        PasswordResetToken row = tokenRepository.findByTokenHash(ResetTokens.hash(token)).orElse(null);

        if (row == null || row.getUsedAt() != null || !row.getExpiresAt().isAfter(Instant.now())) {
            return null;
        }

        return row;
    }

    // ---------------------------------------------------------------
    // 3. Set the new password
    // ---------------------------------------------------------------

    @Transactional
    public void confirmReset(String token, String newPassword, String confirmPassword, String clientAddress) {

        String key = "reset-confirm:" + clientAddress;

        if (confirmLimiter.isBlocked(key)) {
            throw ApiException.tooManyRequests("Too many attempts. Please try again later.");
        }

        PasswordResetToken row = findUsable(token);

        if (row == null) {
            confirmLimiter.recordFailure(key);
            throw ApiException.badRequest(INVALID_LINK);
        }

        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw ApiException.badRequest("The two passwords do not match.");
        }

        boolean admin = PasswordResetToken.ADMIN.equals(row.getAccountType());

        String username;
        Employee employee = null;
        Admin adminAccount = null;

        if (admin) {
            adminAccount = adminRepository.findById(Long.parseLong(row.getAccountRef())).orElse(null);
            username = adminAccount == null ? null : adminAccount.getUsername();
        } else {
            employee = employeeRepository.findById(row.getAccountRef()).orElse(null);
            username = employee == null ? null : employee.getUsername();
        }

        if (username == null) {
            throw ApiException.badRequest(INVALID_LINK);
        }

        // Policy check BEFORE the token is burnt, so a weak password can be corrected on the same link.
        String hash = passwordService.hashNew(newPassword, username);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // Atomic single use: only one of several simultaneous requests gets 1 here.
        if (tokenRepository.markUsed(row.getId(), now) != 1) {
            confirmLimiter.recordFailure(key);
            throw ApiException.badRequest(INVALID_LINK);
        }

        if (admin) {
            adminAccount.setPassword(hash);
            adminAccount.setPasswordChangedAt(now);
            adminRepository.save(adminAccount);
        } else {
            employee.setPassword(hash);
            employee.setPasswordChangedAt(now);
            employeeRepository.save(employee);
        }

        // Any other outstanding link for this account dies too (the used one is removed as well).
        tokenRepository.deleteForAccount(row.getAccountType(), row.getAccountRef());

        confirmLimiter.recordSuccess(key);
    }
}
