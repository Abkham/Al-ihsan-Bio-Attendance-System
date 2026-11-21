package com.developers_group.biometricattendance.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * UI/audit mirror of a registered attendance device.
 *
 * The real cryptographic credential (public key, signature counter) lives in
 * Spring Security's own user_credentials table and is the ONLY thing that proves
 * identity. This row adds the human-friendly information (device name, nickname,
 * registration / revocation history).
 *
 * ONE ACTIVE DEVICE PER EMPLOYEE: {@code activeKey} holds the employee id while
 * the row is Active and is NULL otherwise. Its UNIQUE constraint makes the
 * database itself reject a second active device, even for simultaneous requests.
 */
@Entity
@Table(name = "biometric")
@Getter
@Setter
public class Biometric {

    public static final String ACTIVE = "Active";
    public static final String REVOKED = "Revoked";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long biometricId;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(
            name = "employee_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_biometric_employee")
    )
    private Employee employee;

    // WebAuthn credential id (base64url). Not a fingerprint image or template.
    @Column(
            name = "fingerprint_id",
            nullable = false,
            length = 2048
    )
    private String fingerprintId;

    // Legacy NOT NULL column. The real public key is kept by Spring Security.
    @JsonIgnore
    @Lob
    @Column(name = "public_key", nullable = false)
    private String publicKey;

    @Column(name = "sign_count", nullable = false)
    private Long signCount = 0L;

    // Display name of the device, e.g. "SM-A065F (Chrome)" or the employee's nickname.
    private String fingerName;

    // Optional name chosen by the employee, e.g. "My Tecno phone".
    private String deviceNickname;

    // Model reported by the browser when it is available (e.g. "SM-A065F"). Informational only.
    private String deviceModel;

    // e.g. Android, iOS, Windows - informational only.
    private String platform;

    private String browser;

    // Active | Revoked   (legacy rows may say Inactive)
    private String status;

    private Instant registeredAt;

    private Instant revokedAt;

    // SELF | ADMIN:<username> | REPLACED
    private String revokedBy;

    @Column(length = 500)
    private String revokeReason;

    // Employee id while Active, NULL otherwise. UNIQUE => at most one Active device per employee.
    @JsonIgnore
    @Column(name = "active_key", length = 50, unique = true)
    private String activeKey;

    @JsonProperty("employeeId")
    public String getEmployeeId() {
        return employee == null ? null : employee.getEmployeeId();
    }
}
