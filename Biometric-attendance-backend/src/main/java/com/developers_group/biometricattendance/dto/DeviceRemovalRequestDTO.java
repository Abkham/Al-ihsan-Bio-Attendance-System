package com.developers_group.biometricattendance.dto;

import com.developers_group.biometricattendance.entity.Biometric;
import com.developers_group.biometricattendance.entity.DeviceRemovalRequest;
import lombok.Getter;

import java.time.Instant;

/** View of a removal request for the employee and the administrator. */
@Getter
public class DeviceRemovalRequestDTO {

    private final Long id;
    private final String employeeId;
    private final String employeeName;
    private final Long deviceId;
    private final String deviceName;
    private final String requestType;
    private final String reason;
    private final String description;
    private final String status;
    private final Instant requestedAt;
    private final String reviewedBy;
    private final Instant reviewedAt;
    private final String adminComment;
    private final Instant completedAt;

    public DeviceRemovalRequestDTO(DeviceRemovalRequest r) {
        Biometric device = r.getDevice();
        this.id = r.getId();
        this.employeeId = r.getEmployee().getEmployeeId();
        this.employeeName = r.getEmployee().getFullName();
        this.deviceId = device == null ? null : device.getBiometricId();
        this.deviceName = device == null ? null : device.getFingerName();
        this.requestType = r.getRequestType();
        this.reason = r.getReason();
        this.description = r.getDescription();
        this.status = r.getStatus();
        this.requestedAt = r.getRequestedAt();
        this.reviewedBy = r.getReviewedBy();
        this.reviewedAt = r.getReviewedAt();
        this.adminComment = r.getAdminComment();
        this.completedAt = r.getCompletedAt();
    }
}
