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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.xpfarm.dailyq.config.DailyQConfig.RawTask;
import org.xpfarm.dailyq.reward.ItemReward;
import org.xpfarm.dailyq.reward.ItemSpec;

/**
 * {@link TaskArchetype#fromConfig}, {@link TaskTier#fromConfig}, and {@link TaskDefinition#from}
 * must map onto exactly the vocabulary {@link org.xpfarm.dailyq.config.ConfigParser} already
 * validates ({@code MINE, HARVEST, CRAFT, KILL, PLACE, TRADE} / {@code easy, medium, stretch}) and
 * reject anything outside it.
 */
final class TaskDefinitionTest {

    @Test
    void archetypeFromConfigMapsEveryKnownSpelling() {
        assertEquals(TaskArchetype.MINE, TaskArchetype.fromConfig("MINE"));
        assertEquals(TaskArchetype.HARVEST, TaskArchetype.fromConfig("HARVEST"));
        assertEquals(TaskArchetype.CRAFT, TaskArchetype.fromConfig("CRAFT"));
        assertEquals(TaskArchetype.KILL, TaskArchetype.fromConfig("KILL"));
        assertEquals(TaskArchetype.PLACE, TaskArchetype.fromConfig("PLACE"));
        assertEquals(TaskArchetype.TRADE, TaskArchetype.fromConfig("TRADE"));
    }

    @Test
    void archetypeFromConfigIsCaseInsensitive() {
        assertEquals(TaskArchetype.MINE, TaskArchetype.fromConfig("mine"));
    }

    @Test
    void archetypeFromConfigRejectsUnknownValue() {
        assertThrows(IllegalArgumentException.class, () -> TaskArchetype.fromConfig("FISH"));
    }

    @Test
    void archetypeFromConfigRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> TaskArchetype.fromConfig(null));
    }

    @Test
    void tierFromConfigMapsEveryConfigSpelling() {
        assertEquals(TaskTier.EASY, TaskTier.fromConfig("easy"));
        assertEquals(TaskTier.MEDIUM, TaskTier.fromConfig("medium"));
        assertEquals(TaskTier.STRETCH, TaskTier.fromConfig("stretch"));
    }

    @Test
    void tierFromConfigRejectsUnknownValue() {
        assertThrows(IllegalArgumentException.class, () -> TaskTier.fromConfig("hard"));
    }

    @Test
    void tierFromConfigRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> TaskTier.fromConfig(null));
    }

    @Test
    void fromMapsRawTaskOntoTaskDefinition() {
        RawTask raw = new RawTask(
                "mine_iron", "MINE", "IRON_ORE", 32, "easy", List.of(new ItemSpec(Material.BREAD, 4)));

        TaskDefinition definition = TaskDefinition.from(raw);

        assertEquals(
                new TaskDefinition(
                        "mine_iron",
                        TaskArchetype.MINE,
                        "IRON_ORE",
                        32,
                        TaskTier.EASY,
                        ItemReward.of(List.of(new ItemSpec(Material.BREAD, 4)))),
                definition);
    }

    @Test
    void fromRejectsUnknownArchetype() {
        RawTask raw = new RawTask("bad", "FISH", "SALMON", 1, "easy", List.of());

        assertThrows(IllegalArgumentException.class, () -> TaskDefinition.from(raw));
    }

    @Test
    void fromRejectsUnknownTier() {
        RawTask raw = new RawTask("bad", "MINE", "IRON_ORE", 1, "hard", List.of());

        assertThrows(IllegalArgumentException.class, () -> TaskDefinition.from(raw));
    }
}
