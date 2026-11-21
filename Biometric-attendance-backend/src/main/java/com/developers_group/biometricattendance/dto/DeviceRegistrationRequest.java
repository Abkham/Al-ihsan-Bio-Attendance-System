package com.developers_group.biometricattendance.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * What the browser sends after a successful WebAuthn registration so the
 * server can store a human friendly record. Everything except the credential
 * id is INFORMATIONAL: the browser can lie about it, so it is never used for security.
 */
@Getter
@Setter
public class DeviceRegistrationRequest {

    // WebAuthn credential id (must already exist in Spring Security's user_credentials table)
    private String fingerprintId;

    // Optional name typed by the employee, e.g. "My Tecno phone"
    private String deviceNickname;

    // Reported by the browser when available (Chromium client hints) - may be null
    private String deviceModel;

    private String platform;

    private String browser;
}
