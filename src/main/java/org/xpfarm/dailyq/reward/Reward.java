/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.reward;

import java.util.List;
import org.bukkit.inventory.ItemStack;

/**
 * Something a player can be granted for completing a task, a login-streak day, or a milestone.
 *
 * <p>{@code permits} currently lists only {@link ItemReward}. A future {@code TokenReward} (a
 * currency-style reward redeemable outside items) is expected to join this sealed hierarchy
 * later; it is intentionally not added yet.
 */
public sealed interface Reward permits ItemReward {

    /** Materializes this reward as live {@link ItemStack}s. Bukkit-touching; needs a running server. */
    List<ItemStack> toItemStacks();
}
