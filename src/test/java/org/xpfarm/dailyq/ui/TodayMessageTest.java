/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.xpfarm.dailyq.reward.ItemReward;
import org.xpfarm.dailyq.reward.ItemSpec;
import org.xpfarm.dailyq.streak.StreakOutcome;
import org.xpfarm.dailyq.task.TaskArchetype;
import org.xpfarm.dailyq.task.TaskDefinition;
import org.xpfarm.dailyq.task.TaskTier;

/**
 * {@link TodayMessage#render} is pure — no Bukkit server is needed to build or serialize the
 * {@link Component} it returns, so every assertion here works off the plain-text rendering via
 * {@link PlainTextComponentSerializer}.
 */
final class TodayMessageTest {

    private static final TaskDefinition MINE_TASK = new TaskDefinition(
            "mine_iron", TaskArchetype.MINE, "IRON_ORE", 32, TaskTier.EASY,
            ItemReward.of(List.of(new ItemSpec(Material.BREAD, 4))));

    private static final TaskDefinition HARVEST_TASK = new TaskDefinition(
            "harvest_wheat", TaskArchetype.HARVEST, "WHEAT", 64, TaskTier.MEDIUM,
            ItemReward.of(List.of(new ItemSpec(Material.EXPERIENCE_BOTTLE, 4))));

    private static String plainTextOf(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void rendersStreakNumberEachTaskLabelWithProgressAndCountAndAClaimHint() {
        StreakOutcome outcome = new StreakOutcome(5, true, false, false);
        Map<String, Integer> progress = Map.of("mine_iron", 10, "harvest_wheat", 64);

        Component rendered =
                TodayMessage.render("Steve", outcome, 5, List.of(MINE_TASK, HARVEST_TASK), progress);
        String text = plainTextOf(rendered);

        // The streak number.
        assertTrue(text.contains("5"), "expected the streak number 5 in: " + text);

        // Each task's label with progress/count.
        assertTrue(text.contains("Mine"), "expected the mine task's label in: " + text);
        assertTrue(text.contains("32"), "expected the mine task's count in: " + text);
        assertTrue(text.contains("10/32"), "expected the mine task's progress/count in: " + text);

        assertTrue(text.contains("Harvest"), "expected the harvest task's label in: " + text);
        assertTrue(text.contains("64/64"), "expected the harvest task's progress/count in: " + text);

        // A claim hint.
        assertTrue(
                text.toLowerCase(java.util.Locale.ROOT).contains("claim"),
                "expected a claim hint in: " + text);
    }

    @Test
    void firstLoginOutcomeRendersDayOneWording() {
        StreakOutcome outcome = new StreakOutcome(1, true, false, false);

        Component rendered = TodayMessage.render("Alex", outcome, 1, List.of(), Map.of());
        String text = plainTextOf(rendered);

        assertTrue(text.contains("day 1") || text.contains("Day 1"), "expected day-1 wording in: " + text);
        assertTrue(text.contains("1"), "expected the streak number in: " + text);
    }

    @Test
    void nonFirstLoginOutcomeDoesNotRenderDayOneWelcomeWording() {
        StreakOutcome outcome = new StreakOutcome(7, true, false, false);

        Component rendered = TodayMessage.render("Alex", outcome, 7, List.of(), Map.of());
        String text = plainTextOf(rendered);

        assertFalse(text.contains("day 1 of your streak"), "did not expect day-1 wording in: " + text);
    }

    @Test
    void alreadyCountedTodayOutcomeStillRendersStreakAndClaimHint() {
        StreakOutcome outcome = new StreakOutcome(5, false, false, true);

        Component rendered = TodayMessage.render("Steve", outcome, 5, List.of(MINE_TASK), Map.of());
        String text = plainTextOf(rendered);

        assertTrue(text.contains("5"), "expected the streak number in: " + text);
        assertTrue(
                text.toLowerCase(java.util.Locale.ROOT).contains("claim"),
                "expected a claim hint in: " + text);
    }

    @Test
    void missingProgressEntryDefaultsToZero() {
        StreakOutcome outcome = new StreakOutcome(3, true, false, false);

        Component rendered = TodayMessage.render("Steve", outcome, 3, List.of(MINE_TASK), Map.of());
        String text = plainTextOf(rendered);

        assertTrue(text.contains("0/32"), "expected zero progress default in: " + text);
    }
}
