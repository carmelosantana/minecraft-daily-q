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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reads and writes the {@code mailbox} table: one row per unclaimed (or claimed) reward waiting
 * for a player, keyed by an auto-incrementing id.
 *
 * <p>The {@code reward} column stores the raw encoded string a {@code reward.RewardCodec}
 * produces; this class never parses or interprets it, matching the "raw strings from the
 * reward codec" contract at the service layer.
 *
 * <p>Every public method here runs on {@link DatabaseExecutor}'s single writer thread and
 * returns a {@link CompletableFuture}, so callers on Paper's main thread never block on SQLite
 * I/O. Every statement is a {@link PreparedStatement}; no method ever concatenates a
 * caller-supplied value into SQL text.
 */
public final class MailboxDao {

    private final Database database;
    private final DatabaseExecutor executor;

    public MailboxDao(Database database, DatabaseExecutor executor) {
        this.database = Objects.requireNonNull(database, "database");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Inserts a new, unclaimed mailbox entry for {@code player}.
     *
     * @param player     the player the reward is owed to
     * @param reward     the raw, codec-encoded reward string
     * @param createdDay the server day the entry was created on
     * @return a future completing with the new row's generated id
     */
    public CompletableFuture<Long> insert(UUID player, String reward, long createdDay) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reward, "reward");
        return executor.submit(() -> {
            String sql = """
                    INSERT INTO mailbox (player_uuid, reward, created_day, claimed)
                    VALUES (?, ?, ?, 0)
                    """;
            try (PreparedStatement ps =
                    database.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, player.toString());
                ps.setString(2, reward);
                ps.setLong(3, createdDay);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            }
        });
    }

    /**
     * Every unclaimed mailbox entry for {@code player}.
     *
     * @param player the player to read
     * @return a future completing with one {@link MailboxRow} per unclaimed entry, in no
     *         particular order; empty if the player has none
     */
    public CompletableFuture<List<MailboxRow>> listUnclaimed(UUID player) {
        Objects.requireNonNull(player, "player");
        return executor.submit(() -> {
            String sql = """
                    SELECT id, reward, created_day FROM mailbox
                    WHERE player_uuid = ? AND claimed = 0
                    """;
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<MailboxRow> rows = new ArrayList<>();
                    while (rs.next()) {
                        rows.add(new MailboxRow(rs.getLong("id"), rs.getString("reward"), rs.getLong("created_day")));
                    }
                    return rows;
                }
            }
        });
    }

    /**
     * Marks the mailbox entry {@code id} as claimed, removing it from future
     * {@link #listUnclaimed} results. A no-op if no such row exists.
     *
     * @param id the mailbox row to mark claimed
     * @return a future completing once the write has been applied
     */
    public CompletableFuture<Void> markClaimed(long id) {
        return executor.submit(() -> {
            String sql = "UPDATE mailbox SET claimed = 1 WHERE id = ?";
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }
}
