/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.xpfarm.dailyq.bedrock.FloodgateBridge;
import org.xpfarm.dailyq.mailbox.MailboxService;
import org.xpfarm.dailyq.mailbox.PendingReward;
import org.xpfarm.dailyq.storage.PlayerState;
import org.xpfarm.dailyq.storage.PlayerStateDao;
import org.xpfarm.dailyq.storage.TaskProgressDao;
import org.xpfarm.dailyq.task.DailyRotation;
import org.xpfarm.dailyq.task.TaskDefinition;
import org.xpfarm.dailyq.time.DayClock;

/**
 * The DailyQ hub, tasks, streak, and mailbox menus, rendered as a Cumulus form for a Bedrock
 * player or a chest {@link Inventory} for a Java one.
 *
 * <p>Deliberately holds an {@code Optional<FloodgateBridge>} rather than a {@link FloodgateBridge}
 * directly, and never imports {@code org.geysermc.*} itself: {@link #open} is the single call site
 * that decides Bedrock vs. Java, and it does so purely through {@link FloodgateBridge#isBedrock}
 * and {@link FloodgateBridge#openForm} — both expressed in JDK/Bukkit terms — so this class stays
 * loadable on a server without Floodgate at all. When {@code bridge} is empty, or a player is not
 * Bedrock, or a Bedrock send fails for any reason (e.g. the player disconnected between the
 * {@code isBedrock} check and the actual send), every path falls through to the Java chest menu —
 * nobody is ever left without a working menu.
 *
 * <p>Rendering here is deliberately thin: this class is NOT unit tested (see the task-9 report) —
 * both the Cumulus and the chest-inventory paths need a live client/server to exercise, which is
 * exactly the gate-7a runtime-verification limit this design accepts. {@link TodayMessage}, by
 * contrast, is pure and carries the real unit-test coverage for card wording.
 */
public final class DailyUi implements Listener {

    private final Plugin plugin;
    private final Optional<FloodgateBridge> bridge;
    private final MailboxService mailbox;
    private final DailyRotation rotation;
    private final TaskProgressDao progressDao;
    private final PlayerStateDao stateDao;
    private final DayClock dayClock;
    private final Map<UUID, OpenScreen> openScreens = new HashMap<>();

    public DailyUi(
            Plugin plugin, Optional<FloodgateBridge> bridge, MailboxService mailbox, DailyRotation rotation,
            TaskProgressDao progressDao, PlayerStateDao stateDao, DayClock dayClock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.mailbox = Objects.requireNonNull(mailbox, "mailbox");
        this.rotation = Objects.requireNonNull(rotation, "rotation");
        this.progressDao = Objects.requireNonNull(progressDao, "progressDao");
        this.stateDao = Objects.requireNonNull(stateDao, "stateDao");
        this.dayClock = Objects.requireNonNull(dayClock, "dayClock");
    }

    /** Opens the top-level DailyQ menu: navigation to Tasks, Streak, and Mailbox. */
    public void openHub(Player player) {
        Objects.requireNonNull(player, "player");
        open(
                player,
                "DailyQ",
                List.of("Welcome to DailyQ! Choose a menu below."),
                List.of("Tasks", "Streak", "Mailbox", "Close"),
                index -> {
                    switch (index) {
                        case 0 -> openTasks(player);
                        case 1 -> openStreak(player);
                        case 2 -> openMailbox(player);
                        default -> {
                            // Close: nothing else to do.
                        }
                    }
                },
                () -> {
                    // Dismissed without a selection: nothing to do.
                });
    }

    /** Opens today's task set with each task's current progress. */
    public void openTasks(Player player) {
        Objects.requireNonNull(player, "player");
        UUID id = player.getUniqueId();
        long day = dayClock.today();
        List<TaskDefinition> today = rotation.forDay(day);

        List<CompletableFuture<Integer>> counts = new ArrayList<>(today.size());
        for (TaskDefinition task : today) {
            counts.add(progressDao.get(day, task.id(), id));
        }

        CompletableFuture.allOf(counts.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    List<String> lines = new ArrayList<>(today.size());
                    for (int i = 0; i < today.size(); i++) {
                        lines.add(taskLine(today.get(i), counts.get(i).join()));
                    }
                    if (lines.isEmpty()) {
                        lines = List.of("No tasks configured for today.");
                    }
                    List<String> content = lines;
                    runSync(player, () -> open(player, "Today's Tasks", content, List.of("Back"),
                            index -> openHub(player), () -> {
                            }));
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("DailyUi: failed to load task progress for " + id + ": " + ex);
                    return null;
                });
    }

    /** Opens the player's current streak status. */
    public void openStreak(Player player) {
        Objects.requireNonNull(player, "player");
        UUID id = player.getUniqueId();
        stateDao.get(id)
                .thenAccept(state -> {
                    List<String> lines = streakLines(state);
                    runSync(player, () -> open(player, "Your Streak", lines, List.of("Back"),
                            index -> openHub(player), () -> {
                            }));
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("DailyUi: failed to load streak state for " + id + ": " + ex);
                    return null;
                });
    }

    /** Opens the player's mailbox: pending rewards and a "Claim" action. */
    public void openMailbox(Player player) {
        Objects.requireNonNull(player, "player");
        UUID id = player.getUniqueId();
        mailbox.pending(id)
                .thenAccept(pending -> {
                    List<String> lines = pending.isEmpty()
                            ? List.of("No rewards waiting.")
                            : pending.stream().map(DailyUi::mailboxLine).toList();
                    runSync(player, () -> open(player, "Mailbox", lines, List.of("Claim", "Back"), index -> {
                        if (index == 0) {
                            claimMailbox(player);
                        } else {
                            openHub(player);
                        }
                    }, () -> {
                    }));
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("DailyUi: failed to load mailbox for " + id + ": " + ex);
                    return null;
                });
    }

    /**
     * Claims every deliverable pending reward, depositing item stacks straight into the player's
     * inventory and leaving anything that doesn't fit pending for a later claim — see {@link
     * MailboxService#claim} for the partial-fit contract.
     */
    private void claimMailbox(Player player) {
        UUID id = player.getUniqueId();
        Function<List<ItemStack>, List<ItemStack>> deposit = stacks -> {
            // MailboxService.claim composes this closure over CompletableFutures backed by
            // DatabaseExecutor, so it runs on the DB thread, never the main thread. Bukkit
            // inventory access off the main thread is unsafe, so the actual addItem call is
            // marshalled onto the main thread via the scheduler and awaited with a bounded
            // timeout. Any failure to deliver (server stopping, timeout, interruption) is
            // treated as a full miss: the entire input is returned as leftovers so the reward
            // stays pending in the mailbox rather than being marked claimed without delivery.
            ItemStack[] array = stacks.toArray(new ItemStack[0]);
            if (!plugin.isEnabled()) {
                plugin.getLogger()
                        .warning("DailyUi: mailbox deposit for " + id + " skipped; plugin is disabled");
                return stacks;
            }
            try {
                java.util.concurrent.Future<List<ItemStack>> future = Bukkit.getScheduler()
                        .callSyncMethod(plugin, () -> new ArrayList<>(player.getInventory().addItem(array).values()));
                return future.get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                plugin.getLogger()
                        .warning("DailyUi: mailbox deposit for " + id + " timed out waiting on main thread");
                return stacks;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                plugin.getLogger()
                        .warning("DailyUi: mailbox deposit for " + id + " interrupted while waiting on main thread");
                return stacks;
            } catch (java.util.concurrent.ExecutionException e) {
                plugin.getLogger()
                        .warning("DailyUi: mailbox deposit for " + id + " failed on main thread: " + e.getCause());
                return stacks;
            }
        };

        mailbox.claim(id, deposit)
                .whenComplete((result, ex) -> runSync(player, () -> {
                    if (ex != null) {
                        plugin.getLogger().warning("DailyUi: mailbox claim failed for " + id + ": " + ex);
                        return;
                    }
                    player.sendMessage(Component.text(
                            "Claimed " + result.claimedCount() + " reward(s); " + result.remainingCount()
                                    + " still pending.",
                            NamedTextColor.GREEN));
                    openMailbox(player);
                }));
    }

    // --- Bedrock/Java routing --------------------------------------------------------------

    /**
     * Routes {@code title}/{@code contentLines}/{@code buttons} to a Cumulus form when {@code
     * player} is a connected Bedrock client and the bridge is present, otherwise (or if the
     * Bedrock send fails) to a chest {@link Inventory} — see the class javadoc for why every
     * fallback path lands here.
     */
    private void open(
            Player player, String title, List<String> contentLines, List<String> buttons, IntConsumer onSelect,
            Runnable onClose) {
        if (bridge.isPresent() && bridge.get().isBedrock(player.getUniqueId())) {
            String content = String.join("\n", contentLines);
            boolean sent = bridge.get().openForm(player, title, content, buttons, onSelect, onClose);
            if (sent) {
                return;
            }
        }
        openChest(player, title, contentLines, buttons, onSelect);
    }

    private void openChest(
            Player player, String title, List<String> contentLines, List<String> buttons, IntConsumer onSelect) {
        int slots = 1 + buttons.size();
        int rows = Math.min(6, Math.max(1, (slots + 8) / 9));
        int capacity = rows * 9;

        Inventory inventory = Bukkit.createInventory(null, capacity, Component.text(title));
        inventory.setItem(0, infoItem(contentLines));

        int shown = Math.min(buttons.size(), capacity - 1);
        for (int i = 0; i < shown; i++) {
            inventory.setItem(i + 1, buttonItem(buttons.get(i)));
        }
        if (buttons.size() > shown) {
            plugin.getLogger()
                    .warning("DailyUi: menu '" + title + "' has " + buttons.size()
                            + " buttons but only " + shown + " fit in a chest; the rest are not shown");
        }

        openScreens.put(player.getUniqueId(), new OpenScreen(inventory, onSelect, shown));
        player.openInventory(inventory);
    }

    // --- Content formatting ------------------------------------------------------------------

    private static List<String> streakLines(PlayerState state) {
        return List.of(
                "Current streak: Day " + state.streak(),
                state.makeUpUsed() ? "Streak make-up already used." : "Streak make-up available if a day is missed.");
    }

    private static String mailboxLine(PendingReward reward) {
        String items = reward.reward().items().stream()
                .map(spec -> spec.amount() + "x " + spec.material().name())
                .collect(Collectors.joining(", "));
        return items.isEmpty() ? "Reward #" + reward.id() : "Reward #" + reward.id() + ": " + items;
    }

    private static String taskLine(TaskDefinition task, int progress) {
        return label(task) + " - " + progress + "/" + task.count();
    }

    private static String label(TaskDefinition task) {
        return capitalize(task.archetype().name()) + " " + task.count() + " " + titleCase(task.target());
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String titleCase(String value) {
        String[] parts = value.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(capitalize(part));
        }
        return result.toString();
    }

    // --- Chest item rendering ------------------------------------------------------------------

    private static ItemStack infoItem(List<String> lines) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Info", NamedTextColor.GOLD));
            meta.lore(lines.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack buttonItem(String label) {
        ItemStack item = new ItemStack(materialForLabel(label));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(label, NamedTextColor.YELLOW));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Material materialForLabel(String label) {
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "tasks" -> Material.WRITTEN_BOOK;
            case "streak" -> Material.CLOCK;
            case "mailbox", "claim" -> Material.CHEST;
            case "close" -> Material.BARRIER;
            case "back" -> Material.ARROW;
            default -> Material.PAPER;
        };
    }

    // --- Main-thread scheduling ----------------------------------------------------------------

    /**
     * Runs {@code task} on the main thread, hopping via the Bukkit scheduler if called from
     * elsewhere (every DAO/{@link MailboxService} call completes off the main thread — see {@link
     * org.xpfarm.dailyq.storage.DatabaseExecutor}), and skipping entirely if {@code player} has
     * since disconnected.
     */
    private void runSync(Player player, Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            if (player.isOnline()) {
                task.run();
            }
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                task.run();
            }
        });
    }

    // --- Click tracking ------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        OpenScreen screen = openScreens.get(player.getUniqueId());
        if (screen == null) {
            return;
        }
        if (!screen.inventory().equals(event.getView().getTopInventory())) {
            return;
        }

        // Cancelled before anything else runs: without this a player can shift-click or drag a
        // menu item out of the chest and it becomes a real item in their inventory.
        event.setCancelled(true);

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(screen.inventory())) {
            // null: the click landed outside the inventory entirely (raw slot -999).
            // otherwise: the click landed in the player's own inventory, not the menu.
            return;
        }

        int slot = event.getSlot();
        if (slot == 0) {
            // The info item; not clickable.
            return;
        }
        int buttonIndex = slot - 1;
        if (buttonIndex < 0 || buttonIndex >= screen.buttonCount()) {
            return;
        }
        screen.onSelect().accept(buttonIndex);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        UUID id = player.getUniqueId();
        OpenScreen screen = openScreens.get(id);
        if (screen != null && screen.inventory().equals(event.getInventory())) {
            openScreens.remove(id);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        openScreens.remove(event.getPlayer().getUniqueId());
    }

    /** One player's currently open DailyQ chest menu. */
    private record OpenScreen(Inventory inventory, IntConsumer onSelect, int buttonCount) {
    }
}
