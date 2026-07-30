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

/**
 * The result of {@link StreakService#computeOnJoin}: the player's streak after today's login is
 * accounted for, and what that login entitles them to.
 *
 * @param newStreak             the streak count after today's login is applied
 * @param streakRewardClaimable whether a streak reward is available to claim for today; always
 *                              {@code false} when {@code alreadyCountedToday} is {@code true}
 * @param makeUpAvailable       whether the player may spend a streak make-up to cover a forgiven
 *                              miss; only ever {@code true} when a miss was just forgiven
 * @param alreadyCountedToday   whether the player already logged in today, so no streak change
 *                              was made
 */
public record StreakOutcome(
        int newStreak, boolean streakRewardClaimable, boolean makeUpAvailable, boolean alreadyCountedToday) {
}
