/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.xpfarm.dailyq.bedrock.FloodgateBridge;
import org.xpfarm.dailyq.command.DailyCommand;
import org.xpfarm.dailyq.config.ConfigException;
import org.xpfarm.dailyq.config.ConfigParser;
import org.xpfarm.dailyq.config.DailyQConfig;
import org.xpfarm.dailyq.listener.JoinListener;
import org.xpfarm.dailyq.listener.TaskProgressListener;
import org.xpfarm.dailyq.mailbox.MailboxService;
import org.xpfarm.dailyq.storage.Database;
import org.xpfarm.dailyq.storage.DatabaseExecutor;
import org.xpfarm.dailyq.storage.MailboxDao;
import org.xpfarm.dailyq.storage.PlayerStateDao;
import org.xpfarm.dailyq.storage.TaskProgressDao;
import org.xpfarm.dailyq.streak.LoginCalendar;
import org.xpfarm.dailyq.streak.StreakService;
import org.xpfarm.dailyq.task.DailyRotation;
import org.xpfarm.dailyq.task.TaskDefinition;
import org.xpfarm.dailyq.time.DayClock;
import org.xpfarm.dailyq.ui.DailyUi;

/**
 * DailyQ plugin entry point: parses config, opens persistence, and wires the task engine, streak
 * engine, reward mailbox, UI, and the {@code /daily} command surface per the design spec at
 * {@code docs/superpowers/specs/2026-07-30-daily-q-design.md}.
 *
 * <p>{@link #onEnable} never throws past itself: a bad config or a database that will not open logs
 * a clear {@code SEVERE} line and disables the plugin, rather than leaving Paper to surface a raw
 * stack trace. The persistence layer ({@link Database}, {@link DatabaseExecutor}, the DAOs, the
 * mailbox) is built once and survives a config reload; only the config-derived runtime (the clock,
 * rotation, streak service, login calendar, UI, and listeners) is rebuilt by {@link #reloadRuntime}.
 */
public final class DailyQPlugin extends JavaPlugin {

    // Built once at enable, reused across reloads.
    private Database database;
    private DatabaseExecutor dbExecutor;
    private PlayerStateDao stateDao;
    private TaskProgressDao progressDao;
    private MailboxService mailbox;
    private Optional<FloodgateBridge> bridge = Optional.empty();

    // Config-derived; rebuilt by reloadRuntime.
    private volatile DailyQConfig config;
    private volatile DayClock dayClock;
    private volatile DailyRotation rotation;
    private volatile StreakService streak;
    private volatile LoginCalendar calendar;
    private volatile DailyUi ui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        DailyQConfig parsed;
        try {
            parsed = ConfigParser.parse(getConfig());
        } catch (ConfigException e) {
            getLogger().severe("DailyQ configuration is invalid; disabling plugin: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            openStorage(parsed);
        } catch (IOException | SQLException e) {
            getLogger().severe("DailyQ could not open its database; disabling plugin: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.bridge = FloodgateBridge.createIfAvailable();
        wireRuntime(parsed);

        PluginCommand command = getCommand("daily");
        if (command == null) {
            getLogger().severe("DailyQ 'daily' command is not declared in plugin.yml; disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        DailyCommand handler = new DailyCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        getLogger().info("DailyQ enabled.");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (dbExecutor != null) {
            dbExecutor.close();
        }
        if (database != null) {
            try {
                database.close();
            } catch (SQLException e) {
                getLogger().warning("DailyQ failed to close its database cleanly: " + e.getMessage());
            }
        }
    }

    private void openStorage(DailyQConfig parsed) throws IOException, SQLException {
        Path dataFolder = getDataFolder().toPath();
        Files.createDirectories(dataFolder);
        Path dbFile = dataFolder.resolve(parsed.storage().dbFile());
        Path sqliteTmpdir = dataFolder.resolve("sqlite-tmp");
        this.database = Database.open(dbFile, sqliteTmpdir.toString(), parsed.storage().busyTimeoutMs());
        this.dbExecutor = new DatabaseExecutor();
        this.stateDao = new PlayerStateDao(database, dbExecutor);
        this.progressDao = new TaskProgressDao(database, dbExecutor);
        MailboxDao mailboxDao = new MailboxDao(database, dbExecutor);
        this.mailbox = new MailboxService(mailboxDao);
    }

    /**
     * (Re)builds every config-derived component and re-registers the listeners. Safe to call again
     * on reload: it first unregisters this plugin's existing handlers so a reload does not leave a
     * stale rotation or UI wired to events.
     */
    private void wireRuntime(DailyQConfig parsed) {
        this.config = parsed;
        this.dayClock = new DayClock(parsed.reset().hour(), parsed.reset().zone(), Clock.systemUTC());

        List<TaskDefinition> pool = parsed.tasks().pool().stream().map(TaskDefinition::from).toList();
        this.rotation = new DailyRotation(pool, parsed.tasks().perDay());

        int calendarLength = calendarLength(parsed);
        this.calendar = new LoginCalendar(parsed.login().calendar(), calendarLength);
        this.streak = new StreakService(
                parsed.streak().forgivenessMisses(), parsed.streak().makeUpEnabled(), calendarLength);

        HandlerList.unregisterAll(this);
        this.ui = new DailyUi(this, bridge, mailbox, rotation, progressDao, stateDao, dayClock);
        getServer().getPluginManager().registerEvents(ui, this);
        getServer().getPluginManager().registerEvents(
                new JoinListener(this, stateDao, progressDao, mailbox, rotation, streak, calendar, dayClock,
                        parsed.messages().todayCard()),
                this);
        getServer().getPluginManager().registerEvents(
                new TaskProgressListener(this, rotation, progressDao, mailbox, dayClock,
                        parsed.tasks().completionBonus()),
                this);
    }

    /**
     * The login-calendar cycle length: the largest configured calendar day or milestone day, so the
     * shipped {@code [1,7,14,28]} calendar cycles over 28 days. Falls back to 1 for an empty
     * calendar, which {@link LoginCalendar} handles by floor-lookup.
     */
    private static int calendarLength(DailyQConfig parsed) {
        return Stream.concat(
                        parsed.login().calendar().keySet().stream(),
                        parsed.login().milestoneDays().stream())
                .max(Integer::compareTo)
                .orElse(1);
    }

    /**
     * Re-parses {@code config.yml} and, only if it is valid, rebuilds the config-derived runtime.
     *
     * @return {@link Optional#empty()} on success, or the validation error message if the new config
     *     is invalid — in which case the previous configuration is left untouched
     */
    public Optional<String> reloadRuntime() {
        reloadConfig();
        DailyQConfig parsed;
        try {
            parsed = ConfigParser.parse(getConfig());
        } catch (ConfigException e) {
            return Optional.of(e.getMessage());
        }
        wireRuntime(parsed);
        return Optional.empty();
    }

    /** Runs {@code task} on the main thread, hopping via the scheduler if called off it. */
    public void runMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (isEnabled()) {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    public DailyQConfig config() {
        return config;
    }

    public DayClock dayClock() {
        return dayClock;
    }

    public DailyUi ui() {
        return ui;
    }

    public MailboxService mailbox() {
        return mailbox;
    }

    public PlayerStateDao stateDao() {
        return stateDao;
    }
}
