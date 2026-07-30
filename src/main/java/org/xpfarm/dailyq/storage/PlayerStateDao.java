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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reads and writes the {@code player_state} table: one row per player holding their current
 * login streak, last login/claim days, and whether they have already spent their streak
 * make-up.
 *
 * <p>Every public method here runs on {@link DatabaseExecutor}'s single writer thread and
 * returns a {@link CompletableFuture}, so callers on Paper's main thread never block on SQLite
 * I/O. Every statement is a {@link PreparedStatement}; no method ever concatenates a
 * caller-supplied value into SQL text.
 */
public final class PlayerStateDao {

    private final Database database;
    private final DatabaseExecutor executor;

    public PlayerStateDao(Database database, DatabaseExecutor executor) {
        this.database = Objects.requireNonNull(database, "database");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * The persisted state for {@code player}.
     *
     * @param player the player to read
     * @return a future completing with the stored {@link PlayerState}, or
     *         {@link PlayerState#zeroed(UUID)} if {@code player} has no row on file
     */
    public CompletableFuture<PlayerState> get(UUID player) {
        Objects.requireNonNull(player, "player");
        return executor.submit(() -> {
            String sql = """
                    SELECT streak, last_login_day, make_up_used, last_claim_day
                    FROM player_state WHERE player_uuid = ?
                    """;
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return PlayerState.zeroed(player);
                    }
                    return new PlayerState(
                            player,
                            rs.getInt("streak"),
                            rs.getLong("last_login_day"),
                            rs.getInt("make_up_used") != 0,
                            rs.getLong("last_claim_day"));
                }
            }
        });
    }

    /**
     * Writes {@code state} whole, inserting a fresh row or overwriting the one already keyed
     * on {@link PlayerState#player()}.
     *
     * @param state the complete state to persist
     * @return a future completing once the write has been applied
     */
    public CompletableFuture<Void> put(PlayerState state) {
        Objects.requireNonNull(state, "state");
        return executor.submit(() -> {
            String sql = """
                    INSERT INTO player_state (player_uuid, streak, last_login_day, make_up_used, last_claim_day)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET
                        streak = excluded.streak,
                        last_login_day = excluded.last_login_day,
                        make_up_used = excluded.make_up_used,
                        last_claim_day = excluded.last_claim_day
                    """;
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setString(1, state.player().toString());
                ps.setInt(2, state.streak());
                ps.setLong(3, state.lastLoginDay());
                ps.setInt(4, state.makeUpUsed() ? 1 : 0);
                ps.setLong(5, state.lastClaimDay());
                ps.executeUpdate();
            }
            return null;
        });
    }
}
