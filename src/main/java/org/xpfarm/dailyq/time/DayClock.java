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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Computes the current "server day" id used by the streak engine and daily task rotation.
 *
 * <p>A server day does not run midnight-to-midnight: it starts at the configured
 * {@code reset.hour} local time in {@code reset.timezone} (see {@code ResetConfig}) and runs
 * until that same clock time the next calendar day. This class is pure {@code java.time} logic
 * with no Bukkit dependency and no ambient time source — every computation is driven by an
 * injected {@link Clock} or an explicit {@link Instant}, so it is fully deterministic in tests
 * and safe to reuse across the plugin.
 */
public final class DayClock {

    private final int resetHour;
    private final ZoneId zone;
    private final Clock clock;

    public DayClock(int resetHour, ZoneId zone, Clock clock) {
        this.resetHour = resetHour;
        this.zone = zone;
        this.clock = clock;
    }

    /** The server-day id for right now, per the injected {@link Clock}. */
    public long today() {
        return dayFor(clock.instant(), resetHour, zone);
    }

    /**
     * The server-day id for {@code instant}: a stable, ever-increasing integer that ticks over
     * at {@code resetHour} local time in {@code zone} rather than at local midnight.
     */
    public static long dayFor(Instant instant, int resetHour, ZoneId zone) {
        return ZonedDateTime.ofInstant(instant, zone)
                .minusHours(resetHour)
                .toLocalDate()
                .toEpochDay();
    }
}
