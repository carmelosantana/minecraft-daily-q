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

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.xpfarm.dailyq.config.DailyQConfig.LoginConfig;
import org.xpfarm.dailyq.config.DailyQConfig.MessagesConfig;
import org.xpfarm.dailyq.config.DailyQConfig.RawTask;
import org.xpfarm.dailyq.config.DailyQConfig.ResetConfig;
import org.xpfarm.dailyq.config.DailyQConfig.StorageConfig;
import org.xpfarm.dailyq.config.DailyQConfig.StreakConfig;
import org.xpfarm.dailyq.config.DailyQConfig.TasksConfig;
import org.xpfarm.dailyq.reward.ItemSpec;

/**
 * Parses and validates a {@code config.yml}-shaped {@link ConfigurationSection} into a
 * {@link DailyQConfig}.
 *
 * <p>Every value that later code trusts is checked here: numeric ranges, {@code Material} and
 * timezone resolution, and pool tier coverage. A malformed config always fails loudly with a
 * {@link ConfigException} naming the bad key rather than producing a partially-valid config.
 */
public final class ConfigParser {

    /** The task-pool tiers a {@code tasks.pool} must cover at least once each. */
    private static final Set<String> REQUIRED_TIERS = Set.of("easy", "medium", "stretch");

    /** Archetypes the v0.1 daily-task engine recognizes (design spec section 4). */
    private static final Set<String> KNOWN_ARCHETYPES =
            Set.of("MINE", "HARVEST", "CRAFT", "KILL", "PLACE", "TRADE");

    /** Mob-category keywords accepted as a {@code KILL} archetype target, alongside any entity type. */
    private static final Set<String> KILL_TARGET_CATEGORIES = Set.of("HOSTILE", "PASSIVE");

    /**
     * Wildcard target literal accepted for every archetype: matches any target of that archetype
     * (see {@link org.xpfarm.dailyq.task.ProgressEvaluator}), e.g. "place any block" or "craft any
     * tool" from the design spec.
     */
    private static final String ANY_TARGET = "ANY";

    private static final String SERVER_TIMEZONE = "server";

    private ConfigParser() {
    }

    public static DailyQConfig parse(ConfigurationSection root) throws ConfigException {
        if (root == null) {
            throw new ConfigException("config is empty or missing");
        }
        return new DailyQConfig(
                parseReset(root),
                parseTasks(root),
                parseLogin(root),
                parseStreak(root),
                parseMessages(root),
                parseStorage(root));
    }

    // -- reset -----------------------------------------------------------------------------

    private static ResetConfig parseReset(ConfigurationSection root) throws ConfigException {
        ConfigurationSection section = requireSection(root, "reset");
        int hour = section.getInt("hour", -1);
        if (hour < 0 || hour > 23) {
            throw new ConfigException("reset.hour must be 0..23, was: " + hour);
        }
        String timezone = requireString(section, "timezone", "reset.timezone");
        ZoneId zone = SERVER_TIMEZONE.equals(timezone) ? ZoneId.systemDefault() : parseZone(timezone);
        return new ResetConfig(hour, zone);
    }

    private static ZoneId parseZone(String timezone) throws ConfigException {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new ConfigException("reset.timezone: unknown timezone '" + timezone + "'", e);
        }
    }

    // -- tasks -----------------------------------------------------------------------------

    private static TasksConfig parseTasks(ConfigurationSection root) throws ConfigException {
        ConfigurationSection section = requireSection(root, "tasks");
        int perDay = section.getInt("per-day", -1);
        if (perDay < 1) {
            throw new ConfigException("tasks.per-day must be >= 1, was: " + perDay);
        }

        List<ItemSpec> completionBonus =
                parseItemList(section.getMapList("completion-bonus.items"), "tasks.completion-bonus.items");

        List<Map<?, ?>> poolEntries = section.getMapList("pool");
        List<RawTask> pool = new ArrayList<>(poolEntries.size());
        Set<String> tiersSeen = new LinkedHashSet<>();
        for (Map<?, ?> entry : poolEntries) {
            RawTask task = parsePoolEntry(entry);
            tiersSeen.add(task.tier());
            pool.add(task);
        }
        for (String requiredTier : REQUIRED_TIERS) {
            if (!tiersSeen.contains(requiredTier)) {
                throw new ConfigException(
                        "tasks.pool is missing a task with tier '" + requiredTier + "'");
            }
        }

        return new TasksConfig(perDay, completionBonus, pool);
    }

    private static RawTask parsePoolEntry(Map<?, ?> entry) throws ConfigException {
        String id = requireMapString(entry, "id", "tasks.pool");
        String context = "tasks.pool[" + id + "]";

        String archetype = requireMapString(entry, "archetype", context);
        if (!KNOWN_ARCHETYPES.contains(archetype)) {
            throw new ConfigException(context + ".archetype: unknown archetype '" + archetype + "'");
        }

        String tier = requireMapString(entry, "tier", context);
        if (!REQUIRED_TIERS.contains(tier)) {
            throw new ConfigException(context + ".tier: unknown tier '" + tier + "'");
        }

        String target = requireMapString(entry, "target", context);
        validateTarget(archetype, target, context);

        Object countValue = entry.get("count");
        if (!(countValue instanceof Number number)) {
            throw new ConfigException(context + ".count is required and must be a number");
        }
        int count = number.intValue();
        if (count < 1) {
            throw new ConfigException(context + ".count must be >= 1, was: " + count);
        }

        Object rewardValue = entry.get("reward");
        if (!(rewardValue instanceof Map<?, ?> rewardMap)) {
            throw new ConfigException(context + ".reward is required");
        }
        List<ItemSpec> reward = parseItemList(itemsOf(rewardMap, context), context + ".reward.items");

        return new RawTask(id, archetype, target, count, tier, reward);
    }

    /**
     * Confirms {@code target} resolves for its archetype: a block/item Material, or, for KILL, a
     * mob category or entity type. The {@link #ANY_TARGET} wildcard is accepted for every
     * archetype, matching {@link org.xpfarm.dailyq.task.ProgressEvaluator}'s own
     * case-insensitive check.
     */
    private static void validateTarget(String archetype, String target, String context)
            throws ConfigException {
        if (ANY_TARGET.equalsIgnoreCase(target)) {
            return;
        }
        if ("KILL".equals(archetype)) {
            if (KILL_TARGET_CATEGORIES.contains(target) || isEntityType(target)) {
                return;
            }
            throw new ConfigException(context + ".target: unknown kill target '" + target + "'");
        }
        if (!isMaterial(target)) {
            throw new ConfigException(context + ".target: unknown material '" + target + "'");
        }
    }

    private static boolean isEntityType(String name) {
        try {
            EntityType.valueOf(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // -- login -----------------------------------------------------------------------------

    private static LoginConfig parseLogin(ConfigurationSection root) throws ConfigException {
        ConfigurationSection section = requireSection(root, "login");

        Set<Integer> milestoneDays = new LinkedHashSet<>(section.getIntegerList("milestone-days"));

        List<Map<?, ?>> calendarEntries = section.getMapList("calendar");
        Map<Integer, List<ItemSpec>> calendar = new LinkedHashMap<>();
        for (Map<?, ?> entry : calendarEntries) {
            Object dayValue = entry.get("day");
            if (!(dayValue instanceof Number number)) {
                throw new ConfigException("login.calendar entry is missing a numeric 'day'");
            }
            int day = number.intValue();
            String context = "login.calendar[" + day + "]";

            Object rewardValue = entry.get("reward");
            if (!(rewardValue instanceof Map<?, ?> rewardMap)) {
                throw new ConfigException(context + ".reward is required");
            }
            List<ItemSpec> reward = parseItemList(itemsOf(rewardMap, context), context + ".reward.items");
            calendar.put(day, reward);
        }

        return new LoginConfig(milestoneDays, calendar);
    }

    // -- streak / messages / storage --------------------------------------------------------

    private static StreakConfig parseStreak(ConfigurationSection root) throws ConfigException {
        ConfigurationSection section = requireSection(root, "streak");
        int forgivenessMisses = section.getInt("forgiveness-misses", -1);
        if (forgivenessMisses < 0) {
            throw new ConfigException(
                    "streak.forgiveness-misses must be >= 0, was: " + forgivenessMisses);
        }
        boolean makeUpEnabled = section.getBoolean("make-up-enabled");
        return new StreakConfig(forgivenessMisses, makeUpEnabled);
    }

    private static MessagesConfig parseMessages(ConfigurationSection root) throws ConfigException {
        ConfigurationSection section = requireSection(root, "messages");
        return new MessagesConfig(section.getBoolean("today-card"), section.getBoolean("toast"));
    }

    private static StorageConfig parseStorage(ConfigurationSection root) throws ConfigException {
        ConfigurationSection section = requireSection(root, "storage");
        String dbFile = requireString(section, "db-file", "storage.db-file");
        int busyTimeoutMs = section.getInt("busy-timeout-ms", -1);
        if (busyTimeoutMs < 0) {
            throw new ConfigException(
                    "storage.busy-timeout-ms must be >= 0, was: " + busyTimeoutMs);
        }
        return new StorageConfig(dbFile, busyTimeoutMs);
    }

    // -- shared item-list parsing ------------------------------------------------------------

    private static List<Map<?, ?>> itemsOf(Map<?, ?> rewardMap, String context) throws ConfigException {
        Object items = rewardMap.get("items");
        if (!(items instanceof List<?> list)) {
            throw new ConfigException(context + ".reward.items is required");
        }
        List<Map<?, ?>> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> map)) {
                throw new ConfigException(context + ".reward.items entries must be item maps");
            }
            result.add(map);
        }
        return result;
    }

    private static List<ItemSpec> parseItemList(List<Map<?, ?>> rawItems, String context)
            throws ConfigException {
        List<ItemSpec> items = new ArrayList<>(rawItems.size());
        for (int i = 0; i < rawItems.size(); i++) {
            items.add(parseItemSpec(rawItems.get(i), context + "[" + i + "]"));
        }
        return items;
    }

    private static ItemSpec parseItemSpec(Map<?, ?> entry, String context) throws ConfigException {
        String materialName = requireMapString(entry, "material", context);
        if (!isMaterial(materialName)) {
            throw new ConfigException(context + ".material: unknown material '" + materialName + "'");
        }
        Material material = Material.valueOf(materialName);

        Object amountValue = entry.get("amount");
        if (!(amountValue instanceof Number number)) {
            throw new ConfigException(context + ".amount is required and must be a number");
        }
        int amount = number.intValue();
        if (amount < 1) {
            throw new ConfigException(context + ".amount must be >= 1, was: " + amount);
        }

        return new ItemSpec(material, amount);
    }

    private static boolean isMaterial(String name) {
        try {
            Material.valueOf(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // -- generic section/value helpers -------------------------------------------------------

    private static ConfigurationSection requireSection(ConfigurationSection root, String path)
            throws ConfigException {
        ConfigurationSection section = root.getConfigurationSection(path);
        if (section == null) {
            throw new ConfigException(path + " section is required");
        }
        return section;
    }

    private static String requireString(ConfigurationSection section, String key, String path)
            throws ConfigException {
        String value = section.getString(key);
        if (value == null || value.isBlank()) {
            throw new ConfigException(path + " is required");
        }
        return value;
    }

    private static String requireMapString(Map<?, ?> map, String key, String context)
            throws ConfigException {
        Object value = map.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new ConfigException(context + "." + key + " is required");
        }
        return string;
    }
}
