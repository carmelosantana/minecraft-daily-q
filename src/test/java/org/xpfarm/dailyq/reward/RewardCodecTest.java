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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * {@link RewardCodec} operates only on {@link ItemSpec} (a {@link Material} enum name and an
 * int), never a live {@link org.bukkit.inventory.ItemStack}, so this test needs no running
 * Bukkit server: {@code Material} is a plain enum on the paper-api classpath.
 */
final class RewardCodecTest {

    @Test
    void multiItemRewardRoundTripsAndPreservesOrder() {
        ItemReward reward =
                ItemReward.of(
                        List.of(
                                new ItemSpec(Material.DIAMOND, 3),
                                new ItemSpec(Material.BREAD, 12),
                                new ItemSpec(Material.IRON_INGOT, 5)));

        String encoded = RewardCodec.encode(reward);
        ItemReward decoded = RewardCodec.decode(encoded);

        assertEquals(reward, decoded);
        assertEquals(
                List.of(Material.DIAMOND, Material.BREAD, Material.IRON_INGOT),
                decoded.items().stream().map(ItemSpec::material).toList(),
                "decode must preserve encode order");
    }

    @Test
    void emptyRewardRoundTripsToEmptyReward() {
        ItemReward reward = ItemReward.of(List.of());

        String encoded = RewardCodec.encode(reward);
        ItemReward decoded = RewardCodec.decode(encoded);

        assertEquals("", encoded);
        assertEquals(ItemReward.of(List.of()), decoded);
        assertTrue(decoded.items().isEmpty());
    }

    @Test
    void singleItemRewardRoundTrips() {
        ItemReward reward = ItemReward.of(List.of(new ItemSpec(Material.EXPERIENCE_BOTTLE, 4)));

        assertEquals(reward, RewardCodec.decode(RewardCodec.encode(reward)));
    }

    @Test
    void malformedEntryMissingAmountThrowsWithBadTokenInMessage() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> RewardCodec.decode("DIAMOND"));
        assertTrue(e.getMessage().contains("DIAMOND"), "message should name the bad token: " + e.getMessage());
    }

    @Test
    void malformedEntryNonNumericAmountThrowsWithBadTokenInMessage() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> RewardCodec.decode("DIAMOND:notanumber"));
        assertTrue(
                e.getMessage().contains("notanumber"),
                "message should name the bad token: " + e.getMessage());
    }

    @Test
    void malformedEntryUnknownMaterialThrowsWithBadTokenInMessage() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> RewardCodec.decode("NOT_A_MATERIAL:2"));
        assertTrue(
                e.getMessage().contains("NOT_A_MATERIAL"),
                "message should name the bad token: " + e.getMessage());
    }

    @Test
    void oneMalformedEntryAmongValidOnesStillThrowsNamingIt() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> RewardCodec.decode("DIAMOND:2;BREAD:notanumber;IRON_INGOT:1"));
        assertTrue(
                e.getMessage().contains("notanumber"),
                "message should name the bad token: " + e.getMessage());
    }
}
