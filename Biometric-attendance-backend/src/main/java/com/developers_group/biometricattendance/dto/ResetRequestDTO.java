package com.developers_group.biometricattendance.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetRequestDTO {
    // "ADMIN" or "EMPLOYEE" - same role choice as the login screen
    private String role;
    private String username;
    private String email;
}
