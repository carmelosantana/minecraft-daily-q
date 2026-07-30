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

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.xpfarm.dailyq.reward.ItemReward;
import org.xpfarm.dailyq.reward.ItemSpec;

/**
 * {@link ProgressEvaluator#increment} is the pure archetype/target matching rule the listener
 * (Task 10) will feed Bukkit events into, so every branch here is exercised without any Bukkit
 * runtime involved.
 */
final class ProgressEvaluatorTest {

    private static final ItemReward REWARD = ItemReward.of(List.of(new ItemSpec(Material.BREAD, 1)));

    private static TaskDefinition def(TaskArchetype archetype, String target) {
        return new TaskDefinition("t", archetype, target, 10, TaskTier.EASY, REWARD);
    }

    @Test
    void exactArchetypeAndTargetMatchReturnsSignalAmount() {
        TaskDefinition definition = def(TaskArchetype.MINE, "IRON_ORE");
        ProgressSignal signal = new ProgressSignal(TaskArchetype.MINE, "IRON_ORE", 5);

        assertEquals(5, ProgressEvaluator.increment(definition, signal));
    }

    @Test
    void targetMatchIsCaseInsensitive() {
        TaskDefinition definition = def(TaskArchetype.MINE, "IRON_ORE");
        ProgressSignal signal = new ProgressSignal(TaskArchetype.MINE, "iron_ore", 5);

        assertEquals(5, ProgressEvaluator.increment(definition, signal));
    }

    @Test
    void archetypeMismatchReturnsZeroEvenWithMatchingTarget() {
        TaskDefinition definition = def(TaskArchetype.MINE, "IRON_ORE");
        ProgressSignal signal = new ProgressSignal(TaskArchetype.HARVEST, "IRON_ORE", 5);

        assertEquals(0, ProgressEvaluator.increment(definition, signal));
    }

    @Test
    void targetMismatchReturnsZeroEvenWithMatchingArchetype() {
        TaskDefinition definition = def(TaskArchetype.MINE, "IRON_ORE");
        ProgressSignal signal = new ProgressSignal(TaskArchetype.MINE, "GOLD_ORE", 5);

        assertEquals(0, ProgressEvaluator.increment(definition, signal));
    }

    @Test
    void anyWildcardMatchesAnyTarget() {
        TaskDefinition definition = def(TaskArchetype.CRAFT, "ANY");
        ProgressSignal signal = new ProgressSignal(TaskArchetype.CRAFT, "STICK", 3);

        assertEquals(3, ProgressEvaluator.increment(definition, signal));
    }

    @Test
    void anyWildcardStillRequiresArchetypeMatch() {
        TaskDefinition definition = def(TaskArchetype.CRAFT, "ANY");
        ProgressSignal signal = new ProgressSignal(TaskArchetype.PLACE, "STICK", 3);

        assertEquals(0, ProgressEvaluator.increment(definition, signal));
    }

    @Test
    void killHostileCategoryMatchesHostileSignal() {
        TaskDefinition definition = def(TaskArchetype.KILL, "HOSTILE");
        ProgressSignal signal = new ProgressSignal(TaskArchetype.KILL, "HOSTILE", 1);

        assertEquals(1, ProgressEvaluator.increment(definition, signal));
    }

    @Test
    void killHostileCategoryDoesNotMatchPassiveSignal() {
        TaskDefinition definition = def(TaskArchetype.KILL, "HOSTILE");
        ProgressSignal signal = new ProgressSignal(TaskArchetype.KILL, "PASSIVE", 1);

        assertEquals(0, ProgressEvaluator.increment(definition, signal));
    }

    @Test
    void killPassiveCategoryMatchesPassiveSignal() {
        TaskDefinition definition = def(TaskArchetype.KILL, "PASSIVE");
        ProgressSignal signal = new ProgressSignal(TaskArchetype.KILL, "PASSIVE", 1);

        assertEquals(1, ProgressEvaluator.increment(definition, signal));
    }

    @Test
    void killSpecificEntityTypeTargetMatchesSameEntitySignal() {
        TaskDefinition definition = def(TaskArchetype.KILL, "ZOMBIE");
        ProgressSignal signal = new ProgressSignal(TaskArchetype.KILL, "ZOMBIE", 1);

        assertEquals(1, ProgressEvaluator.increment(definition, signal));
    }

    @Test
    void killSpecificEntityTypeTargetDoesNotMatchCategorySignal() {
        TaskDefinition definition = def(TaskArchetype.KILL, "ZOMBIE");
        ProgressSignal signal = new ProgressSignal(TaskArchetype.KILL, "HOSTILE", 1);

        assertEquals(0, ProgressEvaluator.increment(definition, signal));
    }
}
