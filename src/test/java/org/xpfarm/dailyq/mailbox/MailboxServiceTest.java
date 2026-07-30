/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.mailbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xpfarm.dailyq.reward.ItemReward;
import org.xpfarm.dailyq.reward.ItemSpec;
import org.xpfarm.dailyq.storage.Database;
import org.xpfarm.dailyq.storage.DatabaseExecutor;
import org.xpfarm.dailyq.storage.MailboxDao;

/**
 * Exercises {@link MailboxService} against the real {@link MailboxDao} over a SQLite file in a
 * JUnit {@link TempDir}, following the same setup pattern as {@code storage.PersistenceTest}.
 *
 * <p>{@code ItemStack} cannot be constructed off a live Paper server (its constructor reaches
 * into {@code Registry}, which throws {@code IllegalStateException: No RegistryAccess
 * implementation found} headlessly). So every reward used here has an empty item list: {@link
 * org.xpfarm.dailyq.reward.ItemReward#toItemStacks()} short-circuits to an empty list without
 * constructing anything, keeping {@link MailboxService#claim} exercisable headlessly. The fake
 * {@code deposit} functions never inspect the (always-empty) stacks they're handed; they signal
 * "rejected, nothing fit" by returning a {@code List<ItemStack>} holding a {@code null} element
 * -- non-empty by count, without ever calling an {@code ItemStack} constructor -- and signal
 * "fully delivered" by returning {@link List#of()}.
 */
final class MailboxServiceTest {

    private static final long AWAIT_SECONDS = 5;

    private DatabaseExecutor executor;
    private Database database;
    private MailboxService service;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("dailyq.db");
        Path tmpdir = tempDir.resolve("sqlite-tmp");
        database = Database.open(dbFile, tmpdir.toString(), 5_000);
        executor = new DatabaseExecutor();
        MailboxDao dao = new MailboxDao(database, executor);
        service = new MailboxService(dao);
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.close();
        database.close();
    }

    private static List<ItemStack> rejected() {
        List<ItemStack> leftovers = new ArrayList<>();
        leftovers.add(null);
        return leftovers;
    }

    @Test
    void grantThenPendingReturnsTheDecodedReward() throws Exception {
        UUID player = UUID.randomUUID();
        ItemReward reward = ItemReward.of(List.of(new ItemSpec(Material.DIAMOND, 3)));

        service.grant(player, reward, 100L).get(AWAIT_SECONDS, TimeUnit.SECONDS);
        List<PendingReward> pending = service.pending(player).get(AWAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(1, pending.size());
        assertEquals(reward, pending.get(0).reward());
    }

    @Test
    void claimWithAcceptingDepositMarksAllClaimedAndReportsNoneRemaining() throws Exception {
        UUID player = UUID.randomUUID();
        ItemReward reward = ItemReward.of(List.of());
        service.grant(player, reward, 100L).get(AWAIT_SECONDS, TimeUnit.SECONDS);
        service.grant(player, reward, 100L).get(AWAIT_SECONDS, TimeUnit.SECONDS);

        ClaimResult result =
                service.claim(player, stacks -> List.<ItemStack>of()).get(AWAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(2, result.claimedCount());
        assertEquals(0, result.remainingCount());
        assertTrue(service.pending(player).get(AWAIT_SECONDS, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    void claimWithRejectingDepositLeavesRowsPendingAndReportsRemaining() throws Exception {
        UUID player = UUID.randomUUID();
        ItemReward reward = ItemReward.of(List.of());
        service.grant(player, reward, 100L).get(AWAIT_SECONDS, TimeUnit.SECONDS);
        service.grant(player, reward, 100L).get(AWAIT_SECONDS, TimeUnit.SECONDS);

        ClaimResult result =
                service.claim(player, stacks -> rejected()).get(AWAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(0, result.claimedCount());
        assertEquals(2, result.remainingCount());
        assertEquals(2, service.pending(player).get(AWAIT_SECONDS, TimeUnit.SECONDS).size());
    }

    @Test
    void claimWithOneRejectionAmongAcceptancesLeavesOnlyThatRowPending() throws Exception {
        UUID player = UUID.randomUUID();
        ItemReward reward = ItemReward.of(List.of());
        service.grant(player, reward, 100L).get(AWAIT_SECONDS, TimeUnit.SECONDS);
        service.grant(player, reward, 100L).get(AWAIT_SECONDS, TimeUnit.SECONDS);

        int[] callCount = {0};
        ClaimResult result =
                service.claim(
                                player,
                                stacks -> {
                                    callCount[0]++;
                                    // Reject only the first call this claim() makes; accept the rest.
                                    return callCount[0] == 1 ? rejected() : List.<ItemStack>of();
                                })
                        .get(AWAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(1, result.claimedCount());
        assertEquals(1, result.remainingCount());
        assertEquals(1, service.pending(player).get(AWAIT_SECONDS, TimeUnit.SECONDS).size());
    }
}
