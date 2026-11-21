package com.developers_group.biometricattendance.security;

import com.developers_group.biometricattendance.entity.Employee;
import com.developers_group.biometricattendance.repository.EmployeeRepository;
import com.developers_group.biometricattendance.service.DeviceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Server-side "one device per account" gate for Spring Security's own WebAuthn endpoints.
 *
 *  POST /webauthn/register/options  -> 409 when the account already has a device
 *  POST /webauthn/register          -> same, and serialised per employee so that two
 *                                      simultaneous requests cannot both succeed
 *                                      (the UNIQUE key on user_credentials is the final backstop)
 *  DELETE /webauthn/register/{id}   -> refused: devices are removed through
 *                                      /api/devices/** where the password is confirmed
 */
public class DeviceRegistrationGuardFilter extends OncePerRequestFilter {

    private final EmployeeRepository employeeRepository;
    private final DeviceService deviceService;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public DeviceRegistrationGuardFilter(EmployeeRepository employeeRepository, DeviceService deviceService) {
        this.employeeRepository = employeeRepository;
        this.deviceService = deviceService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return !(path.equals("/webauthn/register") || path.startsWith("/webauthn/register/"));
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String method = request.getMethod();
        String path = path(request);

        if ("DELETE".equals(method)) {
            JsonErrors.write(response, HttpServletResponse.SC_FORBIDDEN,
                    "A device can only be removed by the school administrator. Use Settings > Device to request removal.");
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Not an employee session: let the normal authorization rules answer (401 / 403).
        if (!"POST".equals(method) || !AuthNames.isEmployee(authentication)) {
            chain.doFilter(request, response);
            return;
        }

        Employee employee = employeeRepository.findByUsernameIgnoreCase(authentication.getName()).orElse(null);

        if (employee == null) {
            chain.doFilter(request, response);
            return;
        }

        // After an EMERGENCY revocation a new device needs the administrator's verification code first.
        if (DeviceService.recoveryPending(employee)
                && !RecoveryProof.isValid(request, authentication.getName())) {
            JsonErrors.write(response, HttpServletResponse.SC_FORBIDDEN, RecoveryProof.MESSAGE, "RECOVERY_REQUIRED");
            return;
        }

        boolean isRegisterCall = path.equals("/webauthn/register");

        if (!isRegisterCall) {
            // /webauthn/register/options
            if (deviceService.hasRegisteredDevice(employee)) {
                reject(response);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        ReentrantLock lock = locks.computeIfAbsent(employee.getEmployeeId(), k -> new ReentrantLock());
        lock.lock();

        try {

            // Re-check inside the lock: the second of two simultaneous requests stops here.
            if (deviceService.hasRegisteredDevice(employee)) {
                reject(response);
                return;
            }

            try {
                chain.doFilter(request, response);
            } catch (Exception ex) {
                if (isIntegrityViolation(ex)) {
                    reject(response);
                    return;
                }
                throw ex;
            }

        } finally {
            lock.unlock();
        }
    }

    private static void reject(HttpServletResponse response) throws IOException {
        JsonErrors.write(response, HttpServletResponse.SC_CONFLICT,
                DeviceService.ALREADY_REGISTERED, "DEVICE_ALREADY_REGISTERED");
    }

    private static boolean isIntegrityViolation(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof DataIntegrityViolationException
                    || t instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            if (t.getCause() == t) break;
        }
        return false;
    }
}
