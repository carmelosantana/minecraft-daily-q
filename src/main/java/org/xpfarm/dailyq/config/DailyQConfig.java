/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.config;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.xpfarm.dailyq.reward.ItemSpec;

/**
 * The fully parsed, validated shape of {@code config.yml}.
 *
 * <p>Built only by {@link ConfigParser#parse(org.bukkit.configuration.ConfigurationSection)},
 * which never returns an instance holding an out-of-range value, an unresolvable {@code Material},
 * or an unparseable timezone — every field here is already known-good.
 */
public record DailyQConfig(
        ResetConfig reset,
        TasksConfig tasks,
        LoginConfig login,
        StreakConfig streak,
        MessagesConfig messages,
        StorageConfig storage) {

    /**
     * The daily server-day rollover boundary.
     *
     * @param hour local hour of day, 0-23, that the server-day rolls over
     * @param zone the timezone {@code hour} is evaluated in; {@code config.yml}'s literal
     *     {@code server} resolves to {@link ZoneId#systemDefault()} at parse time
     */
    public record ResetConfig(int hour, ZoneId zone) {
    }

    /**
     * The rotating daily task pool and the per-day selection rules.
     *
     * @param perDay how many tasks are drawn from {@code pool} for a given server-day
     * @param completionBonus reward granted once all of the day's tasks are complete
     * @param pool every task the daily rotation can draw from
     */
    public record TasksConfig(int perDay, List<ItemSpec> completionBonus, List<RawTask> pool) {
    }

    /**
     * One entry in the task pool, as authored in config.
     *
     * <p>{@code archetype} and {@code tier} are kept as validated raw strings here; a later task
     * maps them onto the task-engine enums. {@link ConfigParser} has already confirmed each
     * resolves to a known archetype / tier before constructing this record.
     */
    public record RawTask(
            String id, String archetype, String target, int count, String tier, List<ItemSpec> reward) {
    }

    /**
     * The login-streak calendar.
     *
     * @param milestoneDays streak-day numbers that carry a milestone-sized reward
     * @param calendar every configured reward day, keyed by streak-day number
     */
    public record LoginConfig(Set<Integer> milestoneDays, Map<Integer, List<ItemSpec>> calendar) {
    }

    /** Anti-burnout streak rules. */
    public record StreakConfig(int forgivenessMisses, boolean makeUpEnabled) {
    }

    /** Player-facing messaging toggles. */
    public record MessagesConfig(boolean todayCard, boolean toast) {
    }

    /** SQLite persistence settings. */
    public record StorageConfig(String dbFile, int busyTimeoutMs) {
    }
}
