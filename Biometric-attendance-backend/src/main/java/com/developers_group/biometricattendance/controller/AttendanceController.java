package com.developers_group.biometricattendance.controller;

import com.developers_group.biometricattendance.dto.AdminAttendanceDTO;
import com.developers_group.biometricattendance.dto.AttendanceSummaryDTO;
import com.developers_group.biometricattendance.dto.DailyAttendanceSummaryDTO;
import com.developers_group.biometricattendance.dto.WeeklyAttendanceDTO;
import com.developers_group.biometricattendance.entity.Attendance;
import com.developers_group.biometricattendance.security.AccessGuard;
import com.developers_group.biometricattendance.security.FingerprintProof;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import com.developers_group.biometricattendance.service.AttendanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AccessGuard accessGuard;

    public AttendanceController(AttendanceService attendanceService, AccessGuard accessGuard) {
        this.attendanceService = attendanceService;
        this.accessGuard = accessGuard;
    }

    // employeeId is the string Employee ID (e.g. EMP004).
    // Signing in / out needs a FRESH fingerprint check done with the employee's registered device
    // (see FingerprintProof). A password alone is never enough, and the method is always BIOMETRIC.

    @PostMapping("/check-in")
    public ResponseEntity<Attendance> checkIn(
            @RequestParam String employeeId,
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            Authentication authentication,
            HttpServletRequest request) {

        accessGuard.requireSelfEmployee(authentication, employeeId);
        FingerprintProof.require(request, authentication.getName());

        Attendance saved = attendanceService.checkIn(employeeId, latitude, longitude, "BIOMETRIC");

        FingerprintProof.clear(request);

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/check-out")
    public ResponseEntity<Attendance> checkOut(
            @RequestParam String employeeId,
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            Authentication authentication,
            HttpServletRequest request) {

        accessGuard.requireSelfEmployee(authentication, employeeId);
        FingerprintProof.require(request, authentication.getName());

        Attendance saved = attendanceService.checkOut(employeeId, latitude, longitude, "BIOMETRIC");

        FingerprintProof.clear(request);

        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<Attendance>> getAllAttendance() {
        return ResponseEntity.ok(attendanceService.getAllAttendance());
    }

    // All attendance rows with employee names/locations for the administrator table
    // Optional ?date=YYYY-MM-DD returns just that school day.
    @GetMapping("/admin")
    public ResponseEntity<List<AdminAttendanceDTO>> getAllAttendanceForAdmin(
            @RequestParam(required = false) String date) {

        return ResponseEntity.ok(attendanceService.getAttendanceForAdmin(
                date == null || date.isBlank() ? null : LocalDate.parse(date)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttendance(@PathVariable Long id) {

        attendanceService.deleteAttendance(id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<Attendance>> getEmployeeAttendance(@PathVariable String employeeId, Authentication authentication) {
        accessGuard.requireSelfOrAdmin(authentication, employeeId);
        return ResponseEntity.ok(attendanceService.getEmployeeAttendance(employeeId));
    }

    @GetMapping("/employee/{employeeId}/summary")
    public ResponseEntity<AttendanceSummaryDTO> getEmployeeSummary(@PathVariable String employeeId, Authentication authentication) {
        accessGuard.requireSelfOrAdmin(authentication, employeeId);
        return ResponseEntity.ok(attendanceService.getEmployeeSummary(employeeId));
    }

    @GetMapping("/employee/{employeeId}/weekly")
    public ResponseEntity<List<WeeklyAttendanceDTO>> getWeeklyAttendance(
            @PathVariable String employeeId,
            @RequestParam String startDate,
            @RequestParam String endDate,
            Authentication authentication) {

        accessGuard.requireSelfOrAdmin(authentication, employeeId);

        return ResponseEntity.ok(
                attendanceService.getWeeklyAttendance(
                        employeeId,
                        LocalDate.parse(startDate),
                        LocalDate.parse(endDate)));
    }

    @GetMapping("/summary/date/{date}")
    public ResponseEntity<DailyAttendanceSummaryDTO> getDailySummary(@PathVariable String date) {
        return ResponseEntity.ok(attendanceService.getDailySummary(LocalDate.parse(date)));
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<List<Attendance>> getAttendanceByDate(@PathVariable String date) {
        return ResponseEntity.ok(attendanceService.getAttendanceByDate(LocalDate.parse(date)));
    }
}
