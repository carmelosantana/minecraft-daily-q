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

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.xpfarm.dailyq.reward.ItemReward;
import org.xpfarm.dailyq.reward.ItemSpec;

/**
 * Maps a player's login streak to the {@link ItemReward} earned for it, over a repeating cycle of
 * {@code length} calendar days.
 *
 * <p>Pure logic, no Bukkit runtime involved: {@link ItemSpec} carries only a {@code Material}
 * enum constant and an amount, so this is exhaustively unit-testable without a running server.
 */
public final class LoginCalendar {

    private final NavigableMap<Integer, List<ItemSpec>> calendar;
    private final int length;

    /**
     * @param calendar the day-of-cycle to reward-items map; may be sparse, in which case a
     *                 streak landing on an undefined day falls back to the nearest lower defined
     *                 day
     * @param length   the length in days of the repeating cycle
     */
    public LoginCalendar(Map<Integer, List<ItemSpec>> calendar, int length) {
        this.calendar = new TreeMap<>(calendar);
        this.length = length;
    }

    /**
     * Resolves the reward earned for the given streak.
     *
     * <p>The streak maps to cycle day {@code ((streak - 1) % length) + 1}. If that exact day has
     * no entry, the nearest lower defined day is used instead, so a sparse calendar still yields
     * a reward.
     *
     * @param streak the player's current streak (1-based)
     * @return the {@link ItemReward} earned for that streak
     * @throws IllegalStateException if no entry is defined at or before the resolved day
     */
    public ItemReward rewardForStreak(int streak) {
        int day = ((streak - 1) % length) + 1;

        Map.Entry<Integer, List<ItemSpec>> entry = calendar.floorEntry(day);
        if (entry == null) {
            throw new IllegalStateException(
                    "No login-calendar entry defined at or before day " + day + " (cycle length "
                            + length + ", streak " + streak + ")");
        }

        return ItemReward.of(entry.getValue());
    }
}
