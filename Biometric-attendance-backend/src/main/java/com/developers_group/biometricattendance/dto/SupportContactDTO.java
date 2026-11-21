package com.developers_group.biometricattendance.dto;

import lombok.Getter;

/**
 * The REAL support contact shown in the Help Center. Fields are null when the school has
 * not entered them - the page then says so instead of showing an invented number.
 */
@Getter
public class SupportContactDTO {

    private final String schoolName;
    private final String phone;
    private final String email;
    private final String contactName;
    private final String workStart;
    private final String workEnd;

    public SupportContactDTO(String schoolName, String phone, String email, String contactName,
                             String workStart, String workEnd) {
        this.schoolName = schoolName;
        this.phone = phone;
        this.email = email;
        this.contactName = contactName;
        this.workStart = workStart;
        this.workEnd = workEnd;
    }
}
