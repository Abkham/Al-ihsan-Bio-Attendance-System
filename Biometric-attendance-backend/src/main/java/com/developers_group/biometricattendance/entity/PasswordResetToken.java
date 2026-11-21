package com.developers_group.biometricattendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One password-reset request. Only the SHA-256 HASH of the token is stored;
 * the token itself exists only in the e-mail sent to the account owner.
 */
@Entity
@Table(
        name = "password_reset_token",
        indexes = {
                @Index(name = "idx_prt_account", columnList = "account_type, account_ref"),
                @Index(name = "idx_prt_expires", columnList = "expires_at")
        }
)
@Getter
@Setter
public class PasswordResetToken {

    public static final String EMPLOYEE = "EMPLOYEE";
    public static final String ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;

    // Employee id (e.g. EMP004) or the administrator's numeric id as text.
    @Column(name = "account_ref", nullable = false, length = 50)
    private String accountRef;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;
}
