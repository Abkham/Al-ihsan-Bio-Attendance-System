package com.developers_group.biometricattendance.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RevokeDeviceRequest {

    // Required when an EMPLOYEE removes their own device (proves it is really them).
    private String password;

    // Optional note kept in the audit trail.
    private String reason;
}
