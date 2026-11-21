package com.developers_group.biometricattendance.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchoolClockTest {

    @Test
    void todayUsesTheSchoolTimeZoneNotUtc() {
        // 22:30 UTC on 2026-10-27 is already 01:30 on 2026-10-28 in Dar es Salaam (UTC+3).
        Clock fixed = Clock.fixed(Instant.parse("2026-10-27T22:30:00Z"), ZoneId.of("Africa/Dar_es_Salaam"));
        SchoolClock clock = new SchoolClock(fixed);

        assertEquals(LocalDate.of(2026, 10, 28), clock.today());
        assertEquals(1, clock.now().getHour());
    }

    @Test
    void nowHasNoFractionOfASecond() {
        Clock fixed = Clock.fixed(Instant.parse("2026-10-27T20:12:05.123456789Z"), ZoneId.of("Africa/Dar_es_Salaam"));

        assertEquals(0, new SchoolClock(fixed).now().getNano());
        assertEquals("2026-10-27T23:12:05", new SchoolClock(fixed).now().toString());
    }

    @Test
    void weeksRunMondayToSunday() {
        // 2026-10-28 is a Wednesday.
        assertEquals(LocalDate.of(2026, 10, 26), SchoolClock.mondayOf(LocalDate.of(2026, 10, 28)));
        assertEquals(LocalDate.of(2026, 11, 1), SchoolClock.sundayOf(LocalDate.of(2026, 10, 28)));
        // A Sunday belongs to the week that started the previous Monday.
        assertEquals(LocalDate.of(2026, 10, 26), SchoolClock.mondayOf(LocalDate.of(2026, 11, 1)));
        // A Monday is its own week start.
        assertEquals(LocalDate.of(2026, 10, 26), SchoolClock.mondayOf(LocalDate.of(2026, 10, 26)));
    }
}
