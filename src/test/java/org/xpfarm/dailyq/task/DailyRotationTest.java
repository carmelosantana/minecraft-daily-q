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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.xpfarm.dailyq.reward.ItemReward;
import org.xpfarm.dailyq.reward.ItemSpec;

/**
 * {@link DailyRotation#forDay(long)} determinism: identical {@code serverDay} must always produce
 * an identical selection, and the exact indices picked are locked against expected values computed
 * directly from {@code new Random(serverDay)} (bucket sizes 3/2/4, drawn in {@link TaskTier}
 * declaration order EASY, MEDIUM, STRETCH) so a future change to the seeding or draw order would
 * be caught here rather than silently reshuffling every server's daily set.
 */
final class DailyRotationTest {

    private static final ItemReward REWARD = ItemReward.of(List.of(new ItemSpec(Material.BREAD, 1)));

    private static TaskDefinition def(String id, TaskArchetype archetype, TaskTier tier) {
        return new TaskDefinition(id, archetype, "ANY", 1, tier, REWARD);
    }

    /** 3 easy, 2 medium, 4 stretch definitions, matching the bucket sizes the seed math below assumes. */
    private static List<TaskDefinition> pool() {
        return List.of(
                def("e1", TaskArchetype.MINE, TaskTier.EASY),
                def("e2", TaskArchetype.MINE, TaskTier.EASY),
                def("e3", TaskArchetype.MINE, TaskTier.EASY),
                def("m1", TaskArchetype.HARVEST, TaskTier.MEDIUM),
                def("m2", TaskArchetype.HARVEST, TaskTier.MEDIUM),
                def("s1", TaskArchetype.KILL, TaskTier.STRETCH),
                def("s2", TaskArchetype.KILL, TaskTier.STRETCH),
                def("s3", TaskArchetype.KILL, TaskTier.STRETCH),
                def("s4", TaskArchetype.KILL, TaskTier.STRETCH));
    }

    @Test
    void forDayReturnsExactlyPerDayTasksOnePerTier() {
        DailyRotation rotation = new DailyRotation(pool(), 3);

        List<TaskDefinition> selection = rotation.forDay(1L);

        assertEquals(3, selection.size());
        assertEquals(TaskTier.EASY, selection.get(0).tier());
        assertEquals(TaskTier.MEDIUM, selection.get(1).tier());
        assertEquals(TaskTier.STRETCH, selection.get(2).tier());
    }

    @Test
    void forDayIsStableAcrossRepeatedCallsForTheSameDay() {
        DailyRotation rotation = new DailyRotation(pool(), 3);

        List<TaskDefinition> first = rotation.forDay(42L);
        List<TaskDefinition> second = rotation.forDay(42L);
        List<TaskDefinition> third = rotation.forDay(42L);

        assertEquals(first, second);
        assertEquals(first, third);
    }

    @Test
    void forDayIsStableAcrossSeparateRotationInstancesForTheSameDay() {
        // Determinism must not depend on any in-memory state carried by a single instance: a
        // freshly constructed DailyRotation over the same pool must reproduce the same day.
        List<TaskDefinition> first = new DailyRotation(pool(), 3).forDay(7L);
        List<TaskDefinition> second = new DailyRotation(pool(), 3).forDay(7L);

        assertEquals(first, second);
    }

    @Test
    void forDayLocksExactSelectionForSeedOne() {
        // new Random(1L): nextInt(3) = 0, nextInt(2) = 0, nextInt(4) = 1 -> e1, m1, s2.
        DailyRotation rotation = new DailyRotation(pool(), 3);

        List<TaskDefinition> selection = rotation.forDay(1L);

        assertEquals(List.of("e1", "m1", "s2"), idsOf(selection));
    }

    @Test
    void forDayLocksExactSelectionForSeedTwo() {
        // new Random(2L): nextInt(3) = 1, nextInt(2) = 0, nextInt(4) = 3 -> e2, m1, s4.
        DailyRotation rotation = new DailyRotation(pool(), 3);

        List<TaskDefinition> selection = rotation.forDay(2L);

        assertEquals(List.of("e2", "m1", "s4"), idsOf(selection));
    }

    @Test
    void differentDaysGenerallyProduceDifferentSelections() {
        DailyRotation rotation = new DailyRotation(pool(), 3);

        List<TaskDefinition> day1 = rotation.forDay(1L);
        List<TaskDefinition> day2 = rotation.forDay(2L);

        assertNotEquals(day1, day2);
    }

    @Test
    void forDayThrowsWhenATierBucketIsEmpty() {
        List<TaskDefinition> poolMissingMedium =
                List.of(def("e1", TaskArchetype.MINE, TaskTier.EASY), def("s1", TaskArchetype.KILL, TaskTier.STRETCH));
        DailyRotation rotation = new DailyRotation(poolMissingMedium, 3);

        assertThrows(IllegalStateException.class, () -> rotation.forDay(1L));
    }

    @Test
    void selectedDefinitionsComeFromTheConfiguredPool() {
        DailyRotation rotation = new DailyRotation(pool(), 3);

        List<TaskDefinition> selection = rotation.forDay(9L);

        assertTrue(pool().containsAll(selection));
    }

    private static List<String> idsOf(List<TaskDefinition> definitions) {
        return definitions.stream().map(TaskDefinition::id).toList();
    }
}
