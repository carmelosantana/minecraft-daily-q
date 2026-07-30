/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.streak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.xpfarm.dailyq.reward.ItemReward;
import org.xpfarm.dailyq.reward.ItemSpec;

/**
 * {@link LoginCalendar} maps a streak count to a calendar day via {@code ((streak-1) %
 * length) + 1} and falls back to the nearest lower defined day for sparse calendars, so this
 * needs no running Bukkit server: {@code Material} is a plain enum on the paper-api classpath,
 * same as {@link org.xpfarm.dailyq.reward.RewardCodecTest}.
 */
final class LoginCalendarTest {

    private static List<ItemSpec> items(Material material, int amount) {
        return List.of(new ItemSpec(material, amount));
    }

    @Test
    void streakOneMapsToDayOneEntry() {
        LoginCalendar calendar =
                new LoginCalendar(
                        Map.of(
                                1, items(Material.BREAD, 1),
                                7, items(Material.IRON_INGOT, 1),
                                14, items(Material.GOLD_INGOT, 1),
                                28, items(Material.DIAMOND, 1)),
                        28);

        assertEquals(ItemReward.of(items(Material.BREAD, 1)), calendar.rewardForStreak(1));
    }

    @Test
    void streakSevenMapsToDaySevenEntry() {
        LoginCalendar calendar =
                new LoginCalendar(
                        Map.of(
                                1, items(Material.BREAD, 1),
                                7, items(Material.IRON_INGOT, 1),
                                14, items(Material.GOLD_INGOT, 1),
                                28, items(Material.DIAMOND, 1)),
                        28);

        assertEquals(ItemReward.of(items(Material.IRON_INGOT, 1)), calendar.rewardForStreak(7));
    }

    @Test
    void streakFourteenMapsToDayFourteenEntry() {
        LoginCalendar calendar =
                new LoginCalendar(
                        Map.of(
                                1, items(Material.BREAD, 1),
                                7, items(Material.IRON_INGOT, 1),
                                14, items(Material.GOLD_INGOT, 1),
                                28, items(Material.DIAMOND, 1)),
                        28);

        assertEquals(ItemReward.of(items(Material.GOLD_INGOT, 1)), calendar.rewardForStreak(14));
    }

    @Test
    void streakTwentyEightMapsToDayTwentyEightEntry() {
        LoginCalendar calendar =
                new LoginCalendar(
                        Map.of(
                                1, items(Material.BREAD, 1),
                                7, items(Material.IRON_INGOT, 1),
                                14, items(Material.GOLD_INGOT, 1),
                                28, items(Material.DIAMOND, 1)),
                        28);

        assertEquals(ItemReward.of(items(Material.DIAMOND, 1)), calendar.rewardForStreak(28));
    }

    @Test
    void streakTwentyNineLoopsBackToDayOneEntry() {
        LoginCalendar calendar =
                new LoginCalendar(
                        Map.of(
                                1, items(Material.BREAD, 1),
                                7, items(Material.IRON_INGOT, 1),
                                14, items(Material.GOLD_INGOT, 1),
                                28, items(Material.DIAMOND, 1)),
                        28);

        // day = ((29 - 1) % 28) + 1 = (28 % 28) + 1 = 0 + 1 = 1
        assertEquals(ItemReward.of(items(Material.BREAD, 1)), calendar.rewardForStreak(29));
    }

    @Test
    void streakFiftySevenAlsoLoopsBackToDayOneEntry() {
        LoginCalendar calendar =
                new LoginCalendar(Map.of(1, items(Material.BREAD, 1)), 28);

        // day = ((57 - 1) % 28) + 1 = (56 % 28) + 1 = 0 + 1 = 1
        assertEquals(ItemReward.of(items(Material.BREAD, 1)), calendar.rewardForStreak(57));
    }

    @Test
    void undefinedExactDayFallsBackToNearestLowerDefinedDay() {
        // Sparse calendar: only days 1, 7, and 14 defined out of a 28-day cycle.
        LoginCalendar calendar =
                new LoginCalendar(
                        Map.of(
                                1, items(Material.BREAD, 1),
                                7, items(Material.IRON_INGOT, 1),
                                14, items(Material.GOLD_INGOT, 1)),
                        28);

        // Streak 10 -> day 10, undefined -> nearest lower defined day is 7.
        assertEquals(ItemReward.of(items(Material.IRON_INGOT, 1)), calendar.rewardForStreak(10));
    }

    @Test
    void undefinedDayJustBeforeNextDefinedDayStillFallsBackToLowerDay() {
        LoginCalendar calendar =
                new LoginCalendar(
                        Map.of(
                                1, items(Material.BREAD, 1),
                                7, items(Material.IRON_INGOT, 1),
                                14, items(Material.GOLD_INGOT, 1)),
                        28);

        // Streak 13 -> day 13, undefined -> nearest lower defined day is still 7 (not 14).
        assertEquals(ItemReward.of(items(Material.IRON_INGOT, 1)), calendar.rewardForStreak(13));
    }

    @Test
    void undefinedDayImmediatelyAfterDayOneFallsBackToDayOne() {
        LoginCalendar calendar =
                new LoginCalendar(
                        Map.of(
                                1, items(Material.BREAD, 1),
                                14, items(Material.GOLD_INGOT, 1)),
                        28);

        assertEquals(ItemReward.of(items(Material.BREAD, 1)), calendar.rewardForStreak(2));
    }

    @Test
    void sparseCalendarFallbackAlsoAppliesAfterLoopingAround() {
        LoginCalendar calendar =
                new LoginCalendar(
                        Map.of(
                                1, items(Material.BREAD, 1),
                                7, items(Material.IRON_INGOT, 1)),
                        28);

        // Streak 38 -> day ((38-1)%28)+1 = (37%28)+1 = 9+1 = 10, undefined -> falls back to 7.
        assertEquals(ItemReward.of(items(Material.IRON_INGOT, 1)), calendar.rewardForStreak(38));
    }

    @Test
    void noDefinedDayAtOrBeforeRequestedDayThrows() {
        // Calendar with a gap at the very start: nothing defined until day 5, so a streak
        // mapping to day 1-4 has no lower entry to fall back to.
        LoginCalendar calendar = new LoginCalendar(Map.of(5, items(Material.BREAD, 1)), 28);

        assertThrows(IllegalStateException.class, () -> calendar.rewardForStreak(1));
    }
}
