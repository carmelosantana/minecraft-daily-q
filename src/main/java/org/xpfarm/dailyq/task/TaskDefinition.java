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

import org.xpfarm.dailyq.config.DailyQConfig.RawTask;
import org.xpfarm.dailyq.reward.ItemReward;

/**
 * One task-pool entry, fully resolved onto the task-engine vocabulary.
 *
 * <p>{@code target} is kept as the same validated raw string {@link RawTask} carried (a
 * {@code Material} name, or for {@code KILL}, a mob category or {@code EntityType} name) rather
 * than re-parsed here: {@link org.xpfarm.dailyq.config.ConfigParser} already confirmed it resolves,
 * and {@link ProgressEvaluator} only ever needs to compare it against a signal's target string.
 */
public record TaskDefinition(
        String id, TaskArchetype archetype, String target, int count, TaskTier tier, ItemReward reward) {

    /**
     * Maps a parsed {@link RawTask} onto its {@link TaskDefinition}, resolving {@code archetype}
     * and {@code tier} onto their enums and wrapping {@code reward} into an {@link ItemReward}.
     *
     * @throws IllegalArgumentException if {@code raw}'s archetype or tier does not resolve — this
     *     should never happen for a {@link RawTask} produced by {@link
     *     org.xpfarm.dailyq.config.ConfigParser}, which already validates both against the same
     *     vocabulary, but callers constructing a {@code RawTask} directly (e.g. tests) are not
     *     otherwise protected.
     */
    public static TaskDefinition from(RawTask raw) {
        TaskArchetype archetype = TaskArchetype.fromConfig(raw.archetype());
        TaskTier tier = TaskTier.fromConfig(raw.tier());
        return new TaskDefinition(raw.id(), archetype, raw.target(), raw.count(), tier, ItemReward.of(raw.reward()));
    }
}
