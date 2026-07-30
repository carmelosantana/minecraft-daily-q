/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.storage;

import java.util.UUID;

/**
 * A player's login-streak state, as persisted in the {@code player_state} table.
 *
 * @param player       the player this state belongs to
 * @param streak       the current consecutive-login streak
 * @param lastLoginDay the server day the player last logged in, or {@code -1} if never recorded
 * @param makeUpUsed   whether the player has already spent their streak make-up for this streak
 * @param lastClaimDay the server day the player last claimed a streak reward, or {@code -1} if
 *                     never recorded
 */
public record PlayerState(UUID player, int streak, long lastLoginDay, boolean makeUpUsed, long lastClaimDay) {

    /**
     * The state {@link PlayerStateDao#get} returns for a player with no row on file: no streak,
     * no recorded login or claim day, and the streak make-up not yet spent.
     *
     * @param player the player to build the default state for
     * @return a zeroed {@code PlayerState} for {@code player}
     */
    public static PlayerState zeroed(UUID player) {
        return new PlayerState(player, 0, -1L, false, -1L);
    }
}
