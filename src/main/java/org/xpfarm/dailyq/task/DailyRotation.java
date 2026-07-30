/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.task;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The server-wide, date-seeded daily task selection.
 *
 * <p>{@link #forDay(long)} is pure and deterministic: seeding {@link Random} with the
 * {@code serverDay} number itself (rather than, say, {@code System.currentTimeMillis()}) means
 * every server instance, on every machine and locale, computes the exact same selection for the
 * same day with no coordination required — there is no player input and nothing to persist beyond
 * the day number.
 */
public final class DailyRotation {

    private final int perDay;
    private final Map<TaskTier, List<TaskDefinition>> byTier;

    /**
     * @param pool the full task pool a day's selection is drawn from
     * @param perDay how many tasks {@link #forDay(long)} returns (one per {@link TaskTier}, in
     *     {@link TaskTier} declaration order, up to this many tiers)
     */
    public DailyRotation(List<TaskDefinition> pool, int perDay) {
        this.perDay = perDay;
        this.byTier = new EnumMap<>(TaskTier.class);
        for (TaskTier tier : TaskTier.values()) {
            byTier.put(tier, new ArrayList<>());
        }
        for (TaskDefinition definition : pool) {
            byTier.get(definition.tier()).add(definition);
        }
        for (TaskTier tier : TaskTier.values()) {
            byTier.put(tier, List.copyOf(byTier.get(tier)));
        }
    }

    /**
     * Computes the shared daily task selection for {@code serverDay}: one definition drawn from
     * each tier's bucket, in {@link TaskTier} declaration order, using a {@link Random} seeded
     * with {@code serverDay}.
     *
     * @throws IllegalStateException if a tier bucket this selection needs to draw from is empty
     */
    public List<TaskDefinition> forDay(long serverDay) {
        Random random = new Random(serverDay);
        List<TaskDefinition> selection = new ArrayList<>(perDay);
        for (TaskTier tier : TaskTier.values()) {
            if (selection.size() >= perDay) {
                break;
            }
            List<TaskDefinition> bucket = byTier.get(tier);
            if (bucket.isEmpty()) {
                throw new IllegalStateException("no " + tier + " tasks available in pool");
            }
            selection.add(bucket.get(random.nextInt(bucket.size())));
        }
        return List.copyOf(selection);
    }
}
