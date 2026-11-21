package com.developers_group.biometricattendance.dto;

import com.developers_group.biometricattendance.entity.Attendance;
import com.developers_group.biometricattendance.entity.Employee;
import com.developers_group.biometricattendance.entity.SystemSetting;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One row of the administrator's "View Employees Attendance" table. */
@Getter
@Setter
@NoArgsConstructor
public class AdminAttendanceDTO {

    private Long attendanceId;

    private String employeeId;

    private String firstName;

    private String lastName;

    private String employeeName;

    private String department;

    private String position;

    private LocalDate attendanceDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime checkInTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime checkOutTime;

    private Double checkInLatitude;

    private Double checkInLongitude;

    private Double checkOutLatitude;

    private Double checkOutLongitude;

    private String status;

    private Double workingHours;

    private String verificationMethod;

    // Metres from the school centre (null when the point or the school location is missing/invalid)
    private Long checkInDistanceMeters;

    private Long checkOutDistanceMeters;

    // First + middle + last name
    private String middleName;

    public static AdminAttendanceDTO from(Attendance a) {
        return from(a, null);
    }

    public static AdminAttendanceDTO from(Attendance a, SystemSetting school) {

        AdminAttendanceDTO dto = new AdminAttendanceDTO();
        Employee e = a.getEmployee();

        dto.attendanceId = a.getAttendanceId();
        dto.employeeId = e.getEmployeeId();
        dto.firstName = e.getFirstName();
        dto.lastName = e.getLastName();
        dto.middleName = e.getMiddleName();
        dto.employeeName = e.getFullName();
        dto.department = e.getDepartment();
        dto.position = e.getPosition();
        dto.attendanceDate = a.getAttendanceDate();
        dto.checkInTime = a.getCheckInTime();
        dto.checkOutTime = a.getCheckOutTime();
        dto.checkInLatitude = a.getCheckInLatitude();
        dto.checkInLongitude = a.getCheckInLongitude();
        dto.checkOutLatitude = a.getCheckOutLatitude();
        dto.checkOutLongitude = a.getCheckOutLongitude();
        dto.status = a.getStatus();
        dto.workingHours = a.getWorkingHours();
        dto.verificationMethod = a.getVerificationMethod();

        if (school != null) {
            dto.checkInDistanceMeters = distance(school, a.getCheckInLatitude(), a.getCheckInLongitude());
            dto.checkOutDistanceMeters = distance(school, a.getCheckOutLatitude(), a.getCheckOutLongitude());
        }

        return dto;
    }

    private static boolean validPoint(Double lat, Double lon) {
        return lat != null && lon != null && !lat.isNaN() && !lon.isNaN()
                && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    /** Haversine distance in metres, or null when either point is missing or out of range. */
    static Long distance(SystemSetting school, Double lat, Double lon) {

        if (!validPoint(lat, lon) || !validPoint(school.getSchoolLatitude(), school.getSchoolLongitude())) {
            return null;
        }

        double r = 6371000;
        double p1 = Math.toRadians(lat);
        double p2 = Math.toRadians(school.getSchoolLatitude());
        double dp = Math.toRadians(school.getSchoolLatitude() - lat);
        double dl = Math.toRadians(school.getSchoolLongitude() - lon);

        double h = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);

        return Math.round(r * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h)));
    }
}
