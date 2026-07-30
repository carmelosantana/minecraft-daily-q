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
 * The daily-task archetypes the v0.1 task engine recognizes.
 *
 * <p>This is the exact vocabulary {@link org.xpfarm.dailyq.config.ConfigParser} already validates
 * every {@code tasks.pool[].archetype} entry against (design spec section 4) — the names here must
 * stay byte-for-byte identical to {@code ConfigParser.KNOWN_ARCHETYPES} or a config that parses
 * cleanly could still fail to map onto a {@link TaskDefinition}.
 */
public enum TaskArchetype {
    MINE,
    HARVEST,
    CRAFT,
    KILL,
    PLACE,
    TRADE;

    /**
     * Resolves a config-file archetype spelling (case-insensitive) to its enum constant.
     *
     * @throws IllegalArgumentException if {@code value} is not a known archetype
     */
    public static TaskArchetype fromConfig(String value) {
        if (value == null) {
            throw new IllegalArgumentException("archetype is required");
        }
        try {
            return TaskArchetype.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown archetype: '" + value + "'", e);
        }
    }
}
