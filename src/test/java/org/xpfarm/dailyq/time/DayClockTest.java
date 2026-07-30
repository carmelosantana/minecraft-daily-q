/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * {@link DayClock} is pure {@code java.time} logic — every case below feeds a fixed
 * {@link Instant} or {@link Clock} so the server-day boundary math is fully deterministic.
 */
final class DayClockTest {

    // -- resetHour = 0, UTC ------------------------------------------------------------------

    @Test
    void sameUtcDateSharesDayIdWithMidnightReset() {
        Instant early = Instant.parse("2026-07-30T00:00:00Z");
        Instant late = Instant.parse("2026-07-30T23:59:59Z");

        long earlyDay = DayClock.dayFor(early, 0, ZoneOffset.UTC);
        long lateDay = DayClock.dayFor(late, 0, ZoneOffset.UTC);

        assertEquals(earlyDay, lateDay);
    }

    @Test
    void crossingUtcMidnightIncrementsDayIdByOneWithMidnightReset() {
        Instant beforeMidnight = Instant.parse("2026-07-30T23:59:59Z");
        Instant afterMidnight = Instant.parse("2026-07-31T00:00:00Z");

        long before = DayClock.dayFor(beforeMidnight, 0, ZoneOffset.UTC);
        long after = DayClock.dayFor(afterMidnight, 0, ZoneOffset.UTC);

        assertEquals(before + 1, after);
    }

    // -- resetHour = 6, UTC ------------------------------------------------------------------

    @Test
    void justBeforeSixAmResetIsStillPreviousDay() {
        Instant fiveFiftyNine = Instant.parse("2026-07-30T05:59:00Z");
        // The bucket 05:59 on the 30th belongs to opened at 06:00 on the *previous*
        // calendar day and is still open right up to 05:59:59 on the 30th.
        Instant previousBucketStart = Instant.parse("2026-07-29T06:00:00Z");

        long fiveFiftyNineDay = DayClock.dayFor(fiveFiftyNine, 6, ZoneOffset.UTC);
        long previousBucketDay = DayClock.dayFor(previousBucketStart, 6, ZoneOffset.UTC);

        assertEquals(previousBucketDay, fiveFiftyNineDay);
    }

    @Test
    void sixAmResetBoundaryStartsNewDay() {
        Instant fiveFiftyNine = Instant.parse("2026-07-30T05:59:00Z");
        Instant sixAm = Instant.parse("2026-07-30T06:00:00Z");

        long before = DayClock.dayFor(fiveFiftyNine, 6, ZoneOffset.UTC);
        long after = DayClock.dayFor(sixAm, 6, ZoneOffset.UTC);

        assertEquals(before + 1, after);
    }

    // -- non-UTC zone ------------------------------------------------------------------------

    @Test
    void nonUtcZoneShiftsTheBoundary() {
        // 2026-07-30T02:00:00Z is 2026-07-29T22:00:00-04:00 in America/New_York (EDT):
        // still the previous local calendar day at a midnight reset.
        ZoneId newYork = ZoneId.of("America/New_York");
        Instant instant = Instant.parse("2026-07-30T02:00:00Z");

        long nyDay = DayClock.dayFor(instant, 0, newYork);
        long utcDay = DayClock.dayFor(instant, 0, ZoneOffset.UTC);

        assertEquals(LocalDate.of(2026, 7, 29).toEpochDay(), nyDay);
        assertEquals(LocalDate.of(2026, 7, 30).toEpochDay(), utcDay);
        assertNotEquals(utcDay, nyDay);
    }

    // -- instance today() via injected Clock ---------------------------------------------------

    @Test
    void todayDelegatesToDayForUsingInjectedClock() {
        Instant instant = Instant.parse("2026-07-30T05:59:00Z");
        Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
        DayClock dayClock = new DayClock(6, ZoneOffset.UTC, clock);

        assertEquals(DayClock.dayFor(instant, 6, ZoneOffset.UTC), dayClock.today());
    }
}
