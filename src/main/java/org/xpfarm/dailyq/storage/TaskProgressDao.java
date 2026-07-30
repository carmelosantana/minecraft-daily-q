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
 * Reads and writes the {@code task_progress} table: one row per (server day, task, player)
 * holding how many times that player has progressed that task on that day, and whether they
 * have already claimed its reward.
 *
 * <p>Every public method here runs on {@link DatabaseExecutor}'s single writer thread and
 * returns a {@link CompletableFuture}, so callers on Paper's main thread never block on SQLite
 * I/O. Every statement is a {@link PreparedStatement}; no method ever concatenates a
 * caller-supplied value into SQL text.
 */
public final class TaskProgressDao {

    private final Database database;
    private final DatabaseExecutor executor;

    public TaskProgressDao(Database database, DatabaseExecutor executor) {
        this.database = Objects.requireNonNull(database, "database");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Adds {@code delta} to the player's progress on {@code taskId} for {@code day}, creating
     * the row at {@code delta} if none exists yet.
     *
     * <p>A single upsert statement with {@code RETURNING} handles both the insert and the
     * accumulate case and hands back the resulting count in the same round trip, so this never
     * races itself the way a read-then-add-then-write sequence could.
     *
     * @param day    the server day the progress happened on
     * @param taskId the task being progressed
     * @param player the player progressing it
     * @param delta  the amount to add to the existing count
     * @return a future completing with the new, accumulated count
     */
    public CompletableFuture<Integer> increment(long day, String taskId, UUID player, int delta) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(player, "player");
        return executor.submit(() -> {
            String sql = """
                    INSERT INTO task_progress (server_day, task_id, player_uuid, count, claimed)
                    VALUES (?, ?, ?, ?, 0)
                    ON CONFLICT(server_day, task_id, player_uuid) DO UPDATE SET count = count + excluded.count
                    RETURNING count
                    """;
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setLong(1, day);
                ps.setString(2, taskId);
                ps.setString(3, player.toString());
                ps.setInt(4, delta);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt("count");
                }
            }
        });
    }

    /**
     * The player's current progress on {@code taskId} for {@code day}.
     *
     * @param day    the server day to read
     * @param taskId the task to read
     * @param player the player to read
     * @return a future completing with the stored count, or {@code 0} if no row exists
     */
    public CompletableFuture<Integer> get(long day, String taskId, UUID player) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(player, "player");
        return executor.submit(() -> {
            String sql = """
                    SELECT count FROM task_progress
                    WHERE server_day = ? AND task_id = ? AND player_uuid = ?
                    """;
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setLong(1, day);
                ps.setString(2, taskId);
                ps.setString(3, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt("count") : 0;
                }
            }
        });
    }

    /**
     * Marks the player's row for {@code taskId} on {@code day} as claimed. A no-op if no such
     * row exists.
     *
     * @param day    the server day to update
     * @param taskId the task to update
     * @param player the player to update
     * @return a future completing once the write has been applied
     */
    public CompletableFuture<Void> markClaimed(long day, String taskId, UUID player) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(player, "player");
        return executor.submit(() -> {
            String sql = """
                    UPDATE task_progress SET claimed = 1
                    WHERE server_day = ? AND task_id = ? AND player_uuid = ?
                    """;
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setLong(1, day);
                ps.setString(2, taskId);
                ps.setString(3, player.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }
}
