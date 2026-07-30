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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link DailyTaskCompletedEvent} and {@link DailyStreakClaimedEvent} are plain Bukkit event
 * seams for the future questing framework: no logic, nothing fires them in v0.1. {@code
 * HandlerList} instantiates fine headlessly, so this needs no running Bukkit server, same as
 * {@link org.xpfarm.dailyq.reward.RewardCodecTest}.
 */
final class EventTest {

    @Test
    void taskCompletedHandlerListIsNonNull() {
        assertNotNull(DailyTaskCompletedEvent.getHandlerList());
    }

    @Test
    void taskCompletedInstanceHandlersMatchStaticList() {
        DailyTaskCompletedEvent event =
                new DailyTaskCompletedEvent(UUID.randomUUID(), "chop_wood");

        assertSame(DailyTaskCompletedEvent.getHandlerList(), event.getHandlers());
    }

    @Test
    void taskCompletedCarriesConstructorFields() {
        UUID player = UUID.randomUUID();
        DailyTaskCompletedEvent event = new DailyTaskCompletedEvent(player, "chop_wood");

        assertEquals(player, event.getPlayer());
        assertEquals("chop_wood", event.getTaskId());
    }

    @Test
    void streakClaimedHandlerListIsNonNull() {
        assertNotNull(DailyStreakClaimedEvent.getHandlerList());
    }

    @Test
    void streakClaimedInstanceHandlersMatchStaticList() {
        DailyStreakClaimedEvent event = new DailyStreakClaimedEvent(UUID.randomUUID(), 7);

        assertSame(DailyStreakClaimedEvent.getHandlerList(), event.getHandlers());
    }

    @Test
    void streakClaimedCarriesConstructorFields() {
        UUID player = UUID.randomUUID();
        DailyStreakClaimedEvent event = new DailyStreakClaimedEvent(player, 7);

        assertEquals(player, event.getPlayer());
        assertEquals(7, event.getStreak());
    }
}
