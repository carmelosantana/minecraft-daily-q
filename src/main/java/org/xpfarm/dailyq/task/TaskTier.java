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

import java.util.Locale;

/**
 * The three difficulty tiers a {@code tasks.pool} must cover, matching
 * {@link org.xpfarm.dailyq.config.ConfigParser#parse}'s {@code REQUIRED_TIERS} exactly: the
 * config-file spellings are the lowercase {@code easy}/{@code medium}/{@code stretch} strings
 * accepted by {@link #fromConfig(String)}.
 *
 * <p>{@link DailyRotation#forDay(long)} draws exactly one {@link TaskDefinition} per tier, in the
 * declaration order below, so that order is itself part of the seeded-selection contract.
 */
public enum TaskTier {
    EASY,
    MEDIUM,
    STRETCH;

    /**
     * Resolves a config-file tier spelling (case-insensitive) to its enum constant.
     *
     * @throws IllegalArgumentException if {@code value} is not a known tier
     */
    public static TaskTier fromConfig(String value) {
        if (value == null) {
            throw new IllegalArgumentException("tier is required");
        }
        try {
            return TaskTier.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown tier: '" + value + "'", e);
        }
    }
}
