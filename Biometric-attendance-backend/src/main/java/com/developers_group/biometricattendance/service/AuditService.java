package com.developers_group.biometricattendance.service;

import com.developers_group.biometricattendance.entity.AuditLog;
import com.developers_group.biometricattendance.entity.Employee;
import com.developers_group.biometricattendance.entity.EmployeeNotification;
import com.developers_group.biometricattendance.repository.AuditLogRepository;
import com.developers_group.biometricattendance.repository.EmployeeNotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Writes the audit trail and the in-system notifications. Joins the caller's transaction. */
@Service
public class AuditService {

    private final AuditLogRepository auditRepository;
    private final EmployeeNotificationRepository notificationRepository;

    public AuditService(AuditLogRepository auditRepository, EmployeeNotificationRepository notificationRepository) {
        this.auditRepository = auditRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void log(String actorId, String actorRole, String action, String targetEmployeeId,
                    Long deviceId, String description) {

        AuditLog row = new AuditLog();
        row.setActorId(cut(actorId, 80));
        row.setActorRole(cut(actorRole, 20));
        row.setAction(cut(action, 60));
        row.setTargetEmployeeId(cut(targetEmployeeId, 50));
        row.setDeviceId(deviceId);
        row.setDescription(cut(description, 600));
        row.setCreatedAt(Instant.now());

        auditRepository.save(row);
    }

    @Transactional
    public void notify(Employee employee, String type, String title, String message) {

        EmployeeNotification n = new EmployeeNotification();
        n.setEmployee(employee);
        n.setType(cut(type, 40));
        n.setTitle(cut(title, 120));
        n.setMessage(cut(message, 1000));
        n.setCreatedAt(Instant.now());

        notificationRepository.save(n);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> recent(String employeeId) {

        PageRequest page = PageRequest.of(0, 300);

        return employeeId == null || employeeId.isBlank()
                ? auditRepository.findAllByOrderByCreatedAtDescIdDesc(page)
                : auditRepository.findByTargetEmployeeIdOrderByCreatedAtDescIdDesc(employeeId.trim().toUpperCase(), page);
    }

    @Transactional(readOnly = true)
    public List<EmployeeNotification> notificationsFor(String employeeId) {
        return notificationRepository.findByEmployeeEmployeeIdOrderByCreatedAtDescIdDesc(employeeId, PageRequest.of(0, 30));
    }

    @Transactional(readOnly = true)
    public long unreadCount(String employeeId) {
        return notificationRepository.countByEmployeeEmployeeIdAndReadAtIsNull(employeeId);
    }

    @Transactional
    public void markAllRead(String employeeId) {

        Instant now = Instant.now();

        for (EmployeeNotification n : notificationRepository.findByEmployeeEmployeeIdAndReadAtIsNull(employeeId)) {
            n.setReadAt(now);
            notificationRepository.save(n);
        }
    }

    private static String cut(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}
