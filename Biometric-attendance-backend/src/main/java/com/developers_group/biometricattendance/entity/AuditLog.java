package com.developers_group.biometricattendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Append-only history of security-relevant device and account actions. Rows are never updated or deleted. */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_target", columnList = "target_employee_id"),
        @Index(name = "idx_audit_created", columnList = "created_at")
})
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Employee ID, "admin#<id>" or "SYSTEM"
    @Column(name = "actor_id", length = 80)
    private String actorId;

    // EMPLOYEE | ADMIN | SYSTEM
    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Column(length = 60, nullable = false)
    private String action;

    // Plain text (no foreign key) so the history survives even if the employee is deleted later.
    @Column(name = "target_employee_id", length = 50)
    private String targetEmployeeId;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(length = 600)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
