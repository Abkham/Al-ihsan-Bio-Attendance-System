package com.developers_group.biometricattendance.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetConfirmDTO {
    private String token;
    private String newPassword;
    private String confirmPassword;
}
