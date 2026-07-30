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

import org.xpfarm.dailyq.storage.PlayerState;

/**
 * Computes how a player's login streak changes when they join on a given server day.
 *
 * <p>Pure logic, no Bukkit runtime involved: every input is a {@link PlayerState} snapshot and a
 * {@code long} day number, so this is exhaustively unit-testable without a running server.
 *
 * <p>{@code calendarLength} is accepted here (rather than only on {@link LoginCalendar}) so a
 * single {@code forgivenessMisses}/{@code makeUpEnabled}/{@code calendarLength} config triple can
 * be threaded through to construct both this service and its paired {@link LoginCalendar}
 * without the caller re-deriving the length twice.
 */
public final class StreakService {

    private final int forgivenessMisses;
    private final boolean makeUpEnabled;
    private final int calendarLength;

    /**
     * @param forgivenessMisses the number of consecutive missed days that still hold (rather than
     *                          step down) a player's streak
     * @param makeUpEnabled     whether the streak make-up feature is enabled at all
     * @param calendarLength    the length in days of the paired {@link LoginCalendar} cycle
     */
    public StreakService(int forgivenessMisses, boolean makeUpEnabled, int calendarLength) {
        this.forgivenessMisses = forgivenessMisses;
        this.makeUpEnabled = makeUpEnabled;
        this.calendarLength = calendarLength;
    }

    /**
     * Computes the streak outcome for a player joining on server day {@code today}.
     *
     * @param state the player's persisted streak state
     * @param today the current server day
     * @return the resulting streak outcome
     */
    public StreakOutcome computeOnJoin(PlayerState state, long today) {
        if (state.lastLoginDay() == today) {
            return new StreakOutcome(state.streak(), false, false, true);
        }

        int newStreak;
        boolean makeUp = false;

        if (state.lastLoginDay() < 0) {
            // First ever login.
            newStreak = 1;
        } else {
            long missed = (today - state.lastLoginDay()) - 1;
            if (missed <= 0) {
                // Consecutive login.
                newStreak = state.streak() + 1;
            } else if (missed <= forgivenessMisses) {
                // Held: within the forgiveness window.
                newStreak = state.streak();
                makeUp = makeUpEnabled && !state.makeUpUsed();
            } else {
                // Step down beyond the forgiveness window, floored at 1.
                newStreak = Math.max(1, state.streak() - 1);
            }
        }

        return new StreakOutcome(newStreak, true, makeUp, false);
    }

    /** The length in days of the paired {@link LoginCalendar} cycle this service was built with. */
    public int calendarLength() {
        return calendarLength;
    }
}
