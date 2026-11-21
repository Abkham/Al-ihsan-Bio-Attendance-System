package com.developers_group.biometricattendance.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * An employee's request to have their registered device removed.
 *
 *  NORMAL    new / damaged phone, biometric problems. Needs the administrator's approval.
 *  EMERGENCY stolen or lost phone. The administrator can revoke immediately.
 *
 * Status: PENDING -> APPROVED | REJECTED | CANCELLED ; APPROVED -> COMPLETED once the
 * replacement device is registered. Rows are kept forever (history).
 */
@Entity
@Table(name = "device_removal_requests", indexes = {
        @Index(name = "idx_removal_employee", columnList = "employee_id"),
        @Index(name = "idx_removal_status", columnList = "status")
})
@Getter
@Setter
public class DeviceRemovalRequest {

    public static final String NORMAL = "NORMAL";
    public static final String EMERGENCY = "EMERGENCY";

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_removal_employee"))
    @JsonIgnore
    private Employee employee;

    // The device concerned (kept for history; the device row is never deleted when it is revoked).
    @ManyToOne
    @JoinColumn(name = "device_id", foreignKey = @ForeignKey(name = "fk_removal_device"))
    @JsonIgnore
    private Biometric device;

    @Column(name = "request_type", length = 20, nullable = false)
    private String requestType;

    @Column(length = 40, nullable = false)
    private String reason;

    @Column(length = 1000)
    private String description;

    @Column(length = 20, nullable = false)
    private String status;

    // "<employeeId>:<type>" while PENDING, NULL otherwise. UNIQUE => one open request per type
    // per employee, even for double clicks / simultaneous requests.
    @Column(name = "open_key", length = 80, unique = true)
    private String openKey;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "reviewed_by", length = 80)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "admin_comment", length = 600)
    private String adminComment;

    @Column(name = "completed_at")
    private Instant completedAt;
}
