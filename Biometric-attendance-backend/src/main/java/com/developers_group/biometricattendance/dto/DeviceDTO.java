package com.developers_group.biometricattendance.dto;

import com.developers_group.biometricattendance.entity.Biometric;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Public view of a registered attendance device (never contains key material). */
@Getter
public class DeviceDTO {

    private final Long biometricId;
    private final String fingerprintId;
    private final String fingerName;
    private final String employeeId;
    private final String displayName;
    private final String deviceNickname;
    private final String deviceModel;
    private final String platform;
    private final String browser;
    private final String status;
    private final Instant registeredAt;
    private final Instant revokedAt;
    private final String revokedBy;
    private final String employeeName;
    private final String deviceName;
    private final String operatingSystem;
    private final String authenticationMethod = "Fingerprint / screen lock (WebAuthn)";
    private final String revokeReason;

    @Setter
    private Instant lastUsedAt;

    public DeviceDTO(Biometric b) {
        this.biometricId = b.getBiometricId();
        this.fingerprintId = b.getFingerprintId();
        this.fingerName = b.getFingerName();
        this.employeeId = b.getEmployeeId();
        this.displayName = b.getFingerName();
        this.deviceNickname = b.getDeviceNickname();
        this.deviceModel = b.getDeviceModel();
        this.platform = b.getPlatform();
        this.browser = b.getBrowser();
        this.status = b.getStatus();
        this.registeredAt = b.getRegisteredAt();
        this.revokedAt = b.getRevokedAt();
        this.revokedBy = b.getRevokedBy();
        this.employeeName = b.getEmployee() == null ? null : b.getEmployee().getFullName();
        this.deviceName = b.getFingerName();
        this.operatingSystem = b.getPlatform();
        this.revokeReason = b.getRevokeReason();
    }
}
