package com.developers_group.biometricattendance.controller;

import com.developers_group.biometricattendance.dto.SupportContactDTO;
import com.developers_group.biometricattendance.service.SupportContactService;
import com.developers_group.biometricattendance.util.SchoolClock;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SupportController {

    private final SupportContactService supportContactService;
    private final SchoolClock schoolClock;

    public SupportController(SupportContactService supportContactService, SchoolClock schoolClock) {
        this.supportContactService = supportContactService;
        this.schoolClock = schoolClock;
    }

    // Public: needed on the login page / Help Center
    @GetMapping("/support/contact")
    public ResponseEntity<SupportContactDTO> contact() {
        return ResponseEntity.ok(supportContactService.get());
    }

    // The school's "today" (its own time zone) so the browser never guesses from the phone's clock
    @GetMapping("/auth/school-time")
    public ResponseEntity<Map<String, String>> schoolTime() {
        return ResponseEntity.ok(Map.of(
                "today", schoolClock.today().toString(),
                "now", schoolClock.now().toString(),
                "timezone", schoolClock.getZone().getId()));
    }
}
