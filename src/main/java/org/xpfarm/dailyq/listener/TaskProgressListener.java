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
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.plugin.Plugin;
import org.xpfarm.dailyq.event.DailyTaskCompletedEvent;
import org.xpfarm.dailyq.mailbox.MailboxService;
import org.xpfarm.dailyq.reward.ItemReward;
import org.xpfarm.dailyq.reward.ItemSpec;
import org.xpfarm.dailyq.storage.TaskProgressDao;
import org.xpfarm.dailyq.task.DailyRotation;
import org.xpfarm.dailyq.task.ProgressEvaluator;
import org.xpfarm.dailyq.task.ProgressSignal;
import org.xpfarm.dailyq.task.TaskArchetype;
import org.xpfarm.dailyq.task.TaskDefinition;
import org.xpfarm.dailyq.time.DayClock;

/**
 * Turns gameplay events into daily-task progress.
 *
 * <p>This listener owns only the thin event → {@link ProgressSignal} mapping; the archetype/target
 * matching itself lives in the pure, unit-tested {@link ProgressEvaluator}. For each of today's
 * {@link DailyRotation#forDay} tasks it computes how much the event contributes, persists the
 * increment through {@link TaskProgressDao}, fires {@link DailyTaskCompletedEvent} and grants that
 * task's own {@link TaskDefinition#reward()} the first time it crosses its count, and — once every
 * one of the day's tasks is complete for that player — grants the configured completion bonus to
 * the mailbox exactly once.
 *
 * <p><b>KILL category resolution.</b> A killed entity is turned into two candidate targets: its
 * specific {@code EntityType} name (e.g. {@code ZOMBIE}) and its mob category ({@code HOSTILE} if it
 * is a {@link Monster}/{@link Enemy}, else {@code PASSIVE}). Both are evaluated against each task and
 * the larger contribution is taken, so a def targeting {@code ZOMBIE} and a def targeting
 * {@code HOSTILE} both match the same kill without ever double-counting (a single def's target is one
 * string, so at most one candidate matches it).
 *
 * <p><b>Completion-bonus double-grant guard.</b> Completion is recorded in a synthetic
 * {@code task_progress} row keyed by {@link #COMPLETION_MARKER} — a task id that no configured task
 * ever uses and that {@link DailyRotation} never selects. The grant fires only when
 * {@link TaskProgressDao#increment} bumps that marker from 0 to 1; because every DAO write runs on
 * the single DB writer thread, that transition is observed exactly once even if two events complete
 * the set concurrently.
 *
 * <p>Every DAO future completes on the DB thread, so each Bukkit touch (firing the event) is
 * marshalled back to the main thread via the scheduler.
 */
public final class TaskProgressListener implements Listener {

    /** Result slot index of a villager/merchant trade inventory. */
    private static final int MERCHANT_RESULT_SLOT = 2;

    /**
     * Synthetic {@code task_progress.task_id} used only to record that a player has already been
     * granted a given day's completion bonus. Chosen so it can never collide with a configured task
     * id (which are lowercase identifiers) and is never returned by {@link DailyRotation#forDay}.
     */
    private static final String COMPLETION_MARKER = "__completion_bonus__";

    private final Plugin plugin;
    private final DailyRotation rotation;
    private final TaskProgressDao progressDao;
    private final MailboxService mailbox;
    private final DayClock dayClock;
    private final List<ItemSpec> completionBonus;

    public TaskProgressListener(
            Plugin plugin, DailyRotation rotation, TaskProgressDao progressDao, MailboxService mailbox,
            DayClock dayClock, List<ItemSpec> completionBonus) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.rotation = Objects.requireNonNull(rotation, "rotation");
        this.progressDao = Objects.requireNonNull(progressDao, "progressDao");
        this.mailbox = Objects.requireNonNull(mailbox, "mailbox");
        this.dayClock = Objects.requireNonNull(dayClock, "dayClock");
        this.completionBonus = List.copyOf(Objects.requireNonNull(completionBonus, "completionBonus"));
    }

    // --- Event → signal mapping ------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String material = block.getType().name();
        List<ProgressSignal> signals = new ArrayList<>(2);
        // Every break is a candidate MINE; a mature crop break is additionally a HARVEST. A task's
        // archetype decides which of these (if any) it can match.
        signals.add(new ProgressSignal(TaskArchetype.MINE, material, 1));
        if (isMatureCrop(block)) {
            signals.add(new ProgressSignal(TaskArchetype.HARVEST, material, 1));
        }
        apply(event.getPlayer(), signals);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        String material = event.getBlockPlaced().getType().name();
        apply(event.getPlayer(), List.of(new ProgressSignal(TaskArchetype.PLACE, material, 1)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }
        // Counts one recipe batch per craft click. A shift-click that crafts many batches at once
        // fires this event once and is under-counted; verified/tuned at the runtime gate.
        int amount = Math.max(1, result.getAmount());
        apply(player, List.of(new ProgressSignal(TaskArchetype.CRAFT, result.getType().name(), amount)));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        String specific = entity.getType().name();
        String category = isHostile(entity) ? "HOSTILE" : "PASSIVE";
        // Evaluate against both the specific type and its category; ProgressEvaluator returns a
        // non-zero contribution for at most one, so max is the contribution for whichever the task
        // targets. Carried as two signals of the same KILL archetype.
        List<ProgressSignal> signals = List.of(
                new ProgressSignal(TaskArchetype.KILL, specific, 1),
                new ProgressSignal(TaskArchetype.KILL, category, 1));
        apply(killer, signals);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Only merchant (villager/wandering-trader) trades are TRADE progress. Restricting to
        // MerchantInventory here also keeps this handler clear of DailyUi's own chest menus, which
        // are plain chest inventories.
        if (!(event.getInventory() instanceof MerchantInventory merchant)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() != MERCHANT_RESULT_SLOT) {
            return;
        }
        ItemStack result = merchant.getItem(MERCHANT_RESULT_SLOT);
        if (result == null || result.getType().isAir()) {
            return;
        }
        // Counts one traded result stack. Detecting the exact count a shift-click sweeps out of a
        // merchant is imprecise from a click event; verified at the runtime gate.
        int amount = Math.max(1, result.getAmount());
        apply(player, List.of(new ProgressSignal(TaskArchetype.TRADE, result.getType().name(), amount)));
    }

    // --- Progress application --------------------------------------------------------------

    /**
     * Applies {@code signals} to every one of today's tasks, persisting any increment, firing a
     * completion event on the first crossing, and checking for the day's completion bonus once all
     * increments settle.
     */
    private void apply(Player player, List<ProgressSignal> signals) {
        UUID id = player.getUniqueId();
        long day = dayClock.today();
        List<TaskDefinition> today = rotation.forDay(day);

        List<CompletableFuture<Void>> pending = new ArrayList<>();
        for (TaskDefinition def : today) {
            int delta = 0;
            for (ProgressSignal signal : signals) {
                delta += ProgressEvaluator.increment(def, signal);
            }
            if (delta <= 0) {
                continue;
            }
            int applied = delta;
            CompletableFuture<Void> future = progressDao.increment(day, def.id(), id, applied)
                    .thenAccept(newCount -> {
                        int previous = newCount - applied;
                        if (previous < def.count() && newCount >= def.count()) {
                            // Exactly-once: TaskProgressDao.increment is atomic and DatabaseExecutor
                            // serializes all DB writes on one thread, so exactly one increment ever
                            // observes this threshold crossing. Reuse that single condition for both
                            // the event fire and the per-task reward grant below, rather than
                            // re-evaluating it separately.
                            runMain(() -> Bukkit.getPluginManager()
                                    .callEvent(new DailyTaskCompletedEvent(id, def.id())));
                            mailbox.grant(id, def.reward(), day);
                        }
                    });
            pending.add(future);
        }

        if (pending.isEmpty()) {
            return;
        }
        CompletableFuture.allOf(pending.toArray(new CompletableFuture[0]))
                .thenRun(() -> checkCompletionBonus(id, day, today))
                .exceptionally(ex -> {
                    plugin.getLogger().warning("DailyQ: failed to apply task progress for " + id + ": " + ex);
                    return null;
                });
    }

    /**
     * If every one of today's tasks is complete for {@code player}, grants the completion bonus to
     * the mailbox — exactly once, guarded by the {@link #COMPLETION_MARKER} row.
     */
    private void checkCompletionBonus(UUID player, long day, List<TaskDefinition> today) {
        if (today.isEmpty() || completionBonus.isEmpty()) {
            return;
        }
        List<CompletableFuture<Integer>> counts = new ArrayList<>(today.size());
        for (TaskDefinition def : today) {
            counts.add(progressDao.get(day, def.id(), player));
        }
        CompletableFuture.allOf(counts.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    for (int i = 0; i < today.size(); i++) {
                        if (counts.get(i).join() < today.get(i).count()) {
                            return;
                        }
                    }
                    // All complete: claim the one-time marker. Only the 0→1 transition grants.
                    progressDao.increment(day, COMPLETION_MARKER, player, 1)
                            .thenAccept(marker -> {
                                if (marker == 1) {
                                    mailbox.grant(player, ItemReward.of(completionBonus), day);
                                }
                            })
                            .exceptionally(ex -> {
                                plugin.getLogger()
                                        .warning("DailyQ: failed to grant completion bonus for " + player + ": " + ex);
                                return null;
                            });
                })
                .exceptionally(ex -> {
                    plugin.getLogger()
                            .warning("DailyQ: failed to check completion bonus for " + player + ": " + ex);
                    return null;
                });
    }

    // --- Classification helpers ------------------------------------------------------------

    private static boolean isMatureCrop(Block block) {
        return block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge();
    }

    /**
     * Whether {@code entity} counts as a {@code HOSTILE} kill target. {@link Enemy} is the broad
     * marker for aggressive mobs — it covers slimes, ghasts, phantoms, and bosses, and Paper's
     * {@link Monster} interface already extends it, so checking {@code Enemy} alone is sufficient.
     */
    private static boolean isHostile(LivingEntity entity) {
        return entity instanceof Enemy;
    }

    private void runMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
