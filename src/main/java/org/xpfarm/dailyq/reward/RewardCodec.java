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
import java.util.stream.Collectors;
import org.bukkit.Material;

/**
 * Converts an {@link ItemReward} to and from a compact string for DB storage.
 *
 * <p>The encoding is a {@code ;}-separated list of {@code material:amount} entries, where
 * {@code material} is a {@link Material} enum name. This codec is pure: it only ever handles
 * {@link ItemSpec} values (enum names and ints), never a live {@link org.bukkit.inventory.ItemStack},
 * so it needs no running Bukkit server and stays trivially unit-testable.
 */
public final class RewardCodec {

    private static final String ENTRY_SEPARATOR = ";";
    private static final String FIELD_SEPARATOR = ":";

    private RewardCodec() {
    }

    /** Encodes {@code reward} as a {@code material:amount;material:amount;...} string, preserving order. */
    public static String encode(ItemReward reward) {
        return reward.items().stream()
                .map(spec -> spec.material().name() + FIELD_SEPARATOR + spec.amount())
                .collect(Collectors.joining(ENTRY_SEPARATOR));
    }

    /**
     * Decodes a string produced by {@link #encode(ItemReward)} back into an {@code ItemReward}.
     *
     * @throws IllegalArgumentException if any entry is not a valid {@code material:amount} token,
     *     naming the offending token in the message
     */
    public static ItemReward decode(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("encoded reward must not be null");
        }
        if (encoded.isEmpty()) {
            return ItemReward.of(List.of());
        }

        String[] entries = encoded.split(ENTRY_SEPARATOR, -1);
        List<ItemSpec> items = new ArrayList<>(entries.length);
        for (String entry : entries) {
            items.add(parseEntry(entry));
        }
        return ItemReward.of(items);
    }

    private static ItemSpec parseEntry(String entry) {
        String[] fields = entry.split(FIELD_SEPARATOR, -1);
        if (fields.length != 2) {
            throw new IllegalArgumentException(
                    "malformed reward entry (expected 'MATERIAL:amount'): '" + entry + "'");
        }

        String materialName = fields[0];
        Material material;
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "malformed reward entry: unknown material '" + materialName + "' in '" + entry + "'",
                    e);
        }

        String amountText = fields[1];
        int amount;
        try {
            amount = Integer.parseInt(amountText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "malformed reward entry: invalid amount '" + amountText + "' in '" + entry + "'", e);
        }

        return new ItemSpec(material, amount);
    }
}
