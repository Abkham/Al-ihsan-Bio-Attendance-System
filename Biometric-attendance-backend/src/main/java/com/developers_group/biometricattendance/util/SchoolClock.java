package com.developers_group.biometricattendance.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * Single source of truth for "what time / which day is it at the school".
 *
 * Every attendance rule (today's record, late status, weekly report, week
 * comparison) uses this clock instead of the server's default time zone, so
 * the result is the same no matter where the backend is hosted.
 *
 * Configure with  app.timezone  (default Africa/Dar_es_Salaam, UTC+03:00,
 * which is also the time zone of Zanzibar).
 */
@Component
public class SchoolClock {

    private final ZoneId zone;
    private final Clock clock;

    @Autowired
    public SchoolClock(@Value("${app.timezone:Africa/Dar_es_Salaam}") String zoneId) {
        this(Clock.system(ZoneId.of(zoneId)));
    }

    /** Test constructor: a fixed clock makes week/day logic deterministic. */
    public SchoolClock(Clock clock) {
        this.clock = clock;
        this.zone = clock.getZone();
    }

    public ZoneId getZone() {
        return zone;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDateTime now() {
        // Whole seconds only: no fractional part is ever stored or shown (e.g. 23:12:05, not 23:12:05.1234567)
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    /** Monday of the week that contains the given date (weeks run Monday-Sunday). */
    public static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Sunday of the week that contains the given date. */
    public static LocalDate sundayOf(LocalDate date) {
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }
}
