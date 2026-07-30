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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link Database}, {@link DatabaseExecutor}, and the three DAOs against a real
 * SQLite file in a JUnit {@link TempDir}. No running Bukkit server is required: everything
 * here is plain java.sql plus sqlite-jdbc, which is on the test classpath.
 */
final class PersistenceTest {

    private static final long AWAIT_SECONDS = 5;

    private DatabaseExecutor executor;
    private Database database;
    private PlayerStateDao playerStateDao;
    private TaskProgressDao taskProgressDao;
    private MailboxDao mailboxDao;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("dailyq.db");
        Path tmpdir = tempDir.resolve("sqlite-tmp");
        database = Database.open(dbFile, tmpdir.toString(), 5_000);
        executor = new DatabaseExecutor();
        playerStateDao = new PlayerStateDao(database, executor);
        taskProgressDao = new TaskProgressDao(database, executor);
        mailboxDao = new MailboxDao(database, executor);
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.close();
        database.close();
    }

    @Test
    void unknownPlayerGetReturnsZeroedDefault() throws Exception {
        UUID player = UUID.randomUUID();

        PlayerState state = playerStateDao.get(player).get(AWAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(new PlayerState(player, 0, -1L, false, -1L), state);
    }

    @Test
    void putThenGetRoundTrips() throws Exception {
        UUID player = UUID.randomUUID();
        PlayerState written = new PlayerState(player, 7, 42L, true, 41L);

        playerStateDao.put(written).get(AWAIT_SECONDS, TimeUnit.SECONDS);
        PlayerState read = playerStateDao.get(player).get(AWAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(written, read);
    }

    @Test
    void incrementAccumulatesAndReturnsNewCount() throws Exception {
        UUID player = UUID.randomUUID();
        String taskId = "mine_stone";
        long day = 100L;

        int first = taskProgressDao.increment(day, taskId, player, 3).get(AWAIT_SECONDS, TimeUnit.SECONDS);
        int second = taskProgressDao.increment(day, taskId, player, 4).get(AWAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(3, first);
        assertEquals(7, second);
    }

    @Test
    void getReflectsAccumulatedCount() throws Exception {
        UUID player = UUID.randomUUID();
        String taskId = "mine_stone";
        long day = 100L;

        taskProgressDao.increment(day, taskId, player, 3).get(AWAIT_SECONDS, TimeUnit.SECONDS);
        taskProgressDao.increment(day, taskId, player, 5).get(AWAIT_SECONDS, TimeUnit.SECONDS);
        int count = taskProgressDao.get(day, taskId, player).get(AWAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(8, count);
    }

    @Test
    void insertAndListUnclaimedReturnsTheRow() throws Exception {
        UUID player = UUID.randomUUID();

        Long id = mailboxDao.insert(player, "DIAMOND:3", 100L).get(AWAIT_SECONDS, TimeUnit.SECONDS);
        List<MailboxRow> unclaimed = mailboxDao.listUnclaimed(player).get(AWAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(1, unclaimed.size());
        MailboxRow row = unclaimed.get(0);
        assertEquals(id, row.id());
        assertEquals("DIAMOND:3", row.reward());
        assertEquals(100L, row.createdDay());
    }

    @Test
    void markClaimedRemovesRowFromUnclaimedList() throws Exception {
        UUID player = UUID.randomUUID();
        Long id = mailboxDao.insert(player, "DIAMOND:3", 100L).get(AWAIT_SECONDS, TimeUnit.SECONDS);

        mailboxDao.markClaimed(id).get(AWAIT_SECONDS, TimeUnit.SECONDS);
        List<MailboxRow> unclaimed = mailboxDao.listUnclaimed(player).get(AWAIT_SECONDS, TimeUnit.SECONDS);

        assertTrue(unclaimed.isEmpty());
    }
}
