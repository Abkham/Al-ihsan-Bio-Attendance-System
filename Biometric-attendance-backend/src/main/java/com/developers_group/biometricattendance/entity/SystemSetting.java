package com.developers_group.biometricattendance.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "system_setting")
@Getter
@Setter
public class SystemSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long settingId;

    private String schoolName;


    // GPS centre of the school (used for geofencing)
    private Double schoolLatitude;

    private Double schoolLongitude;

    // Official arrival time - check-in after this is "Late"
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm[:ss]")
    private LocalTime workStartTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm[:ss]")
    private LocalTime workEndTime;

    // Allowed radius in metres around the school
    private Double allowedRadius;

    // Support contact shown in the Help Center. Optional: when empty the
    // administrator's own profile contact is used instead (see SupportContactService).
    private String supportPhone;

    private String supportEmail;

}
