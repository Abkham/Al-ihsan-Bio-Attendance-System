package com.developers_group.biometricattendance.security;

import com.developers_group.biometricattendance.entity.Employee;
import com.developers_group.biometricattendance.exception.ApiException;
import com.developers_group.biometricattendance.repository.EmployeeRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Ownership rules that URL patterns cannot express:
 * an employee may only touch their OWN data; an administrator may see any employee.
 */
@Component
public class AccessGuard {

    private final EmployeeRepository employeeRepository;

    public AccessGuard(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /** The Employee ID of the signed-in employee, or null for administrators / anonymous. */
    public String currentEmployeeId(Authentication authentication) {

        if (!AuthNames.isEmployee(authentication)) return null;

        return employeeRepository.findByUsernameIgnoreCase(authentication.getName())
                .map(Employee::getEmployeeId)
                .orElse(null);
    }

    /** Administrator any employee; employee only themselves. */
    public void requireSelfOrAdmin(Authentication authentication, String employeeId) {

        if (AuthNames.isAdmin(authentication)) return;

        requireSelfEmployee(authentication, employeeId);
    }

    /** Only the signed-in employee, and only for their own Employee ID. */
    public void requireSelfEmployee(Authentication authentication, String employeeId) {

        String current = currentEmployeeId(authentication);

        if (current == null || employeeId == null
                || !current.equalsIgnoreCase(employeeId.trim())) {

            throw ApiException.forbidden("You can only access your own information.");
        }
    }

    public void requireSelfAdmin(Authentication authentication, Long adminId) {

        Long current = AuthNames.adminId(authentication);

        if (current == null || !current.equals(adminId)) {
            throw ApiException.forbidden("You can only change your own administrator profile.");
        }
    }

    public void requireNotSelfAdmin(Authentication authentication, Long adminId) {

        Long current = AuthNames.adminId(authentication);

        if (current != null && current.equals(adminId)) {
            throw ApiException.badRequest("You cannot delete the administrator account you are signed in with.");
        }
    }
}
