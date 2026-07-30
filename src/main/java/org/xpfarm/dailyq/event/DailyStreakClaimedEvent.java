/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player claims a login-streak reward.
 *
 * <p>Plain event seam for the future questing framework: no logic here, and nothing fires this
 * event in v0.1. Task 10's services fire it on the main thread once the streak engine wires up.
 */
public class DailyStreakClaimedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final int streak;

    public DailyStreakClaimedEvent(UUID player, int streak) {
        this.player = player;
        this.streak = streak;
    }

    public UUID getPlayer() {
        return player;
    }

    public int getStreak() {
        return streak;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
