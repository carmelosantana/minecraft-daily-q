/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.xpfarm.dailyq.event.DailyStreakClaimedEvent;
import org.xpfarm.dailyq.mailbox.MailboxService;
import org.xpfarm.dailyq.reward.ItemReward;
import org.xpfarm.dailyq.storage.PlayerState;
import org.xpfarm.dailyq.storage.PlayerStateDao;
import org.xpfarm.dailyq.storage.TaskProgressDao;
import org.xpfarm.dailyq.streak.LoginCalendar;
import org.xpfarm.dailyq.streak.StreakOutcome;
import org.xpfarm.dailyq.streak.StreakService;
import org.xpfarm.dailyq.task.DailyRotation;
import org.xpfarm.dailyq.task.TaskDefinition;
import org.xpfarm.dailyq.time.DayClock;
import org.xpfarm.dailyq.ui.TodayMessage;

/**
 * Awards the login-streak reward and shows the "today" join card on {@link PlayerJoinEvent}.
 *
 * <p>The streak decision is pure ({@link StreakService#computeOnJoin}); this listener only wires it
 * to persistence and Bukkit. On a claimable login it grants that day's {@link LoginCalendar} reward
 * to the mailbox, persists the advanced {@link PlayerState}, and fires {@link
 * DailyStreakClaimedEvent} — the framework seam for later phases. All DB work goes through the async
 * DAOs off the main thread; every Bukkit touch (firing the event, sending the card) is marshalled
 * back onto the main thread via the scheduler, because DAO futures complete on the DB writer thread.
 */
public final class JoinListener implements Listener {

    private final Plugin plugin;
    private final PlayerStateDao stateDao;
    private final TaskProgressDao progressDao;
    private final MailboxService mailbox;
    private final DailyRotation rotation;
    private final StreakService streak;
    private final LoginCalendar calendar;
    private final DayClock dayClock;
    private final boolean todayCard;

    public JoinListener(
            Plugin plugin, PlayerStateDao stateDao, TaskProgressDao progressDao, MailboxService mailbox,
            DailyRotation rotation, StreakService streak, LoginCalendar calendar, DayClock dayClock,
            boolean todayCard) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.stateDao = Objects.requireNonNull(stateDao, "stateDao");
        this.progressDao = Objects.requireNonNull(progressDao, "progressDao");
        this.mailbox = Objects.requireNonNull(mailbox, "mailbox");
        this.rotation = Objects.requireNonNull(rotation, "rotation");
        this.streak = Objects.requireNonNull(streak, "streak");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.dayClock = Objects.requireNonNull(dayClock, "dayClock");
        this.todayCard = todayCard;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        long today = dayClock.today();

        stateDao.get(id)
                .thenAccept(state -> {
                    StreakOutcome outcome = streak.computeOnJoin(state, today);
                    if (!outcome.alreadyCountedToday() && outcome.streakRewardClaimable()) {
                        awardStreak(player, state, outcome, today);
                    } else if (todayCard) {
                        showTodayCard(player, outcome, today);
                    }
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("DailyQ: failed to process join streak for " + id + ": " + ex);
                    return null;
                });
    }

    /**
     * Grants the calendar reward to the mailbox, advances and persists the player's streak state,
     * fires {@link DailyStreakClaimedEvent}, then (if enabled) shows the today card.
     */
    private void awardStreak(Player player, PlayerState state, StreakOutcome outcome, long today) {
        UUID id = player.getUniqueId();
        ItemReward reward = calendar.rewardForStreak(outcome.newStreak());
        PlayerState updated = new PlayerState(
                id, outcome.newStreak(), today, state.makeUpUsed(), today);

        mailbox.grant(id, reward, today)
                .thenCompose(v -> stateDao.put(updated))
                .thenRun(() -> runMain(() -> {
                    Bukkit.getPluginManager().callEvent(new DailyStreakClaimedEvent(id, outcome.newStreak()));
                    if (todayCard) {
                        renderCard(player, outcome, today);
                    }
                }))
                .exceptionally(ex -> {
                    plugin.getLogger().warning("DailyQ: failed to award streak for " + id + ": " + ex);
                    return null;
                });
    }

    private void showTodayCard(Player player, StreakOutcome outcome, long today) {
        runMain(() -> renderCard(player, outcome, today));
    }

    /**
     * Loads today's tasks and this player's progress on each, then sends the composed today card on
     * the main thread.
     */
    private void renderCard(Player player, StreakOutcome outcome, long today) {
        UUID id = player.getUniqueId();
        List<TaskDefinition> tasks = rotation.forDay(today);

        List<CompletableFuture<Integer>> counts = new ArrayList<>(tasks.size());
        for (TaskDefinition task : tasks) {
            counts.add(progressDao.get(today, task.id(), id));
        }

        CompletableFuture.allOf(counts.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    Map<String, Integer> progress = new HashMap<>();
                    for (int i = 0; i < tasks.size(); i++) {
                        progress.put(tasks.get(i).id(), counts.get(i).join());
                    }
                    runMain(() -> {
                        if (player.isOnline()) {
                            player.sendMessage(
                                    TodayMessage.render(player.getName(), outcome, outcome.newStreak(), tasks, progress));
                        }
                    });
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("DailyQ: failed to render today card for " + id + ": " + ex);
                    return null;
                });
    }

    /** Runs {@code task} on the main thread, hopping via the scheduler if called off it. */
    private void runMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
