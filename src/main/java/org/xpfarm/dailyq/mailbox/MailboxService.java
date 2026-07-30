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

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.bukkit.inventory.ItemStack;
import org.xpfarm.dailyq.reward.ItemReward;
import org.xpfarm.dailyq.reward.RewardCodec;
import org.xpfarm.dailyq.storage.MailboxDao;
import org.xpfarm.dailyq.storage.MailboxRow;

/**
 * Grants, lists, and claims mailbox rewards, backed by {@link MailboxDao}.
 *
 * <p>Inventory-fit logic is a Bukkit concern the caller owns: {@link #claim} takes a {@code
 * deposit} function that is handed each pending reward's {@link ItemStack ItemStacks} and
 * returns the leftovers that did not fit. A mailbox row is marked claimed only when its
 * leftovers come back empty (fully delivered); otherwise it stays pending for a later claim
 * attempt. This keeps the service testable with a fake {@code deposit} and the real DAO, with no
 * running Bukkit server required.
 *
 * <p>Every method composes the DAO's {@link CompletableFuture}s rather than blocking, so callers
 * on Paper's main thread never wait on SQLite I/O.
 */
public final class MailboxService {

    private final MailboxDao dao;

    public MailboxService(MailboxDao dao) {
        this.dao = Objects.requireNonNull(dao, "dao");
    }

    /**
     * Encodes {@code reward} and inserts a new, unclaimed mailbox entry for {@code player}.
     *
     * @param player the player the reward is owed to
     * @param reward the reward to grant
     * @param day    the server day the entry is created on
     * @return a future completing once the row has been inserted
     */
    public CompletableFuture<Void> grant(UUID player, ItemReward reward, long day) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reward, "reward");
        String encoded = RewardCodec.encode(reward);
        return dao.insert(player, encoded, day).thenApply(id -> null);
    }

    /**
     * Every unclaimed reward waiting for {@code player}, decoded.
     *
     * @param player the player to read
     * @return a future completing with one {@link PendingReward} per unclaimed entry, in no
     *         particular order; empty if the player has none
     */
    public CompletableFuture<List<PendingReward>> pending(UUID player) {
        Objects.requireNonNull(player, "player");
        return dao.listUnclaimed(player)
                .thenApply(
                        rows ->
                                rows.stream()
                                        .map(
                                                row ->
                                                        new PendingReward(
                                                                row.id(), RewardCodec.decode(row.reward())))
                                        .toList());
    }

    /**
     * Attempts to deliver every unclaimed reward for {@code player} via {@code deposit}.
     *
     * <p>For each pending reward, {@code deposit} is called with {@link ItemReward#toItemStacks()}
     * and returns the leftover stacks that did not fit. A reward with empty leftovers is fully
     * delivered and its row is marked claimed; a reward with non-empty leftovers stays pending
     * for a future claim attempt. Rows are processed one at a time so each {@code markClaimed}
     * write completes before the next reward is attempted.
     *
     * @param player  the player claiming their mailbox
     * @param deposit the injected inventory-fit function: given the reward's item stacks,
     *                returns the leftovers that couldn't be delivered (empty if everything fit)
     * @return a future completing with how many rows were fully delivered and how many remain
     *         pending
     */
    public CompletableFuture<ClaimResult> claim(
            UUID player, Function<List<ItemStack>, List<ItemStack>> deposit) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(deposit, "deposit");
        return dao.listUnclaimed(player)
                .thenCompose(
                        rows -> {
                            CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
                            for (MailboxRow row : rows) {
                                chain = chain.thenCompose(claimedSoFar -> deliver(row, deposit, claimedSoFar));
                            }
                            return chain.thenApply(
                                    claimedCount -> new ClaimResult(claimedCount, rows.size() - claimedCount));
                        });
    }

    private CompletableFuture<Integer> deliver(
            MailboxRow row, Function<List<ItemStack>, List<ItemStack>> deposit, int claimedSoFar) {
        ItemReward reward = RewardCodec.decode(row.reward());
        List<ItemStack> leftovers = deposit.apply(reward.toItemStacks());
        if (leftovers.isEmpty()) {
            return dao.markClaimed(row.id()).thenApply(v -> claimedSoFar + 1);
        }
        return CompletableFuture.completedFuture(claimedSoFar);
    }
}
