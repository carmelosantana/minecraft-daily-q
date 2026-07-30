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

import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

/**
 * A {@link Reward} made up of one or more {@link ItemSpec} lines.
 *
 * <p>{@link #toItemStacks()} is the only Bukkit-touching operation on this record; everything
 * else (equality, {@link RewardCodec} encoding) operates purely on the {@code items} list.
 */
public record ItemReward(List<ItemSpec> items) implements Reward {

    /** Wraps {@code items} into an {@code ItemReward}. */
    public static ItemReward of(List<ItemSpec> items) {
        return new ItemReward(items);
    }

    @Override
    public List<ItemStack> toItemStacks() {
        List<ItemStack> stacks = new ArrayList<>(items.size());
        for (ItemSpec spec : items) {
            stacks.add(new ItemStack(spec.material(), spec.amount()));
        }
        return stacks;
    }
}
