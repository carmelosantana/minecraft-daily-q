/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.xpfarm.dailyq.DailyQPlugin;
import org.xpfarm.dailyq.command.DailyCommandRouter.Route;
import org.xpfarm.dailyq.config.DailyQConfig.RawTask;
import org.xpfarm.dailyq.reward.ItemReward;
import org.xpfarm.dailyq.reward.RewardCodec;
import org.xpfarm.dailyq.storage.PlayerState;

/**
 * The {@code /daily} command surface: a thin, dispatch-safe adapter over {@link DailyCommandRouter}.
 *
 * <p>The routing + permission decision lives entirely in {@link DailyCommandRouter#route} (which is
 * unit-tested without Bukkit); this class only resolves the sender's capabilities, then performs the
 * Bukkit-side effect the returned {@link Route} names. Permission checks are done in this body
 * ({@code dailyq.use} for player views, {@code dailyq.admin} for the admin subtree via the router),
 * not merely via {@code plugin.yml}, so the command behaves identically whether typed by a player or
 * dispatched by the Pizza menu with {@code run-as: player} (design spec §13).
 *
 * <p>The plugin-derived services ({@link DailyQPlugin#ui()}, {@link DailyQPlugin#dayClock()}, …) are
 * read live from the plugin on every invocation rather than captured once, so an {@code admin reload}
 * that swaps in a freshly-configured rotation and UI is picked up by the very next command without
 * this executor needing to be re-registered.
 */
public final class DailyCommand implements CommandExecutor, TabCompleter {

    private final DailyQPlugin plugin;

    public DailyCommand(DailyQPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isPlayer = sender instanceof Player;
        boolean isAdmin = sender.hasPermission("dailyq.admin");
        Route route = DailyCommandRouter.route(args, isPlayer, isAdmin);

        switch (route.kind()) {
            case HUB, TASKS, STREAK, CLAIM -> openView(sender, route);
            case DENY_CONSOLE -> sender.sendMessage(
                    Component.text("Only a player can open DailyQ menus.", NamedTextColor.RED));
            case DENY_ADMIN -> sender.sendMessage(
                    Component.text("You do not have permission to use DailyQ admin commands.",
                            NamedTextColor.RED));
            case ADMIN_RELOAD -> adminReload(sender);
            case ADMIN_GRANT -> adminGrant(sender, route.targetPlayer(), route.rewardId());
            case ADMIN_RESET -> adminReset(sender, route.targetPlayer());
            case USAGE -> sendUsage(sender, isAdmin);
            default -> sendUsage(sender, isAdmin);
        }
        return true;
    }

    // --- Player views ----------------------------------------------------------------------

    private void openView(CommandSender sender, Route route) {
        if (!sender.hasPermission("dailyq.use")) {
            sender.sendMessage(Component.text("You do not have permission to use DailyQ.", NamedTextColor.RED));
            return;
        }
        Player player = (Player) sender;
        switch (route.kind()) {
            case HUB -> plugin.ui().openHub(player);
            case TASKS -> plugin.ui().openTasks(player);
            case STREAK -> plugin.ui().openStreak(player);
            case CLAIM -> plugin.ui().openMailbox(player);
            default -> {
                // Unreachable: openView is only called for the four player-view kinds.
            }
        }
    }

    // --- Admin: reload ---------------------------------------------------------------------

    private void adminReload(CommandSender sender) {
        Optional<String> error = plugin.reloadRuntime();
        if (error.isPresent()) {
            sender.sendMessage(Component.text(
                    "DailyQ reload failed; keeping the previous configuration: " + error.get(),
                    NamedTextColor.RED));
        } else {
            sender.sendMessage(Component.text("DailyQ configuration reloaded.", NamedTextColor.GREEN));
        }
    }

    // --- Admin: grant ----------------------------------------------------------------------

    private void adminGrant(CommandSender sender, String targetName, String rewardId) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(Component.text(
                    "Player '" + targetName + "' is not online; grant targets an online player.",
                    NamedTextColor.RED));
            return;
        }
        ItemReward reward = resolveReward(rewardId);
        if (reward == null) {
            sender.sendMessage(Component.text(
                    "Unknown reward '" + rewardId + "'. Use a task-pool id or a MATERIAL:amount payload.",
                    NamedTextColor.RED));
            return;
        }
        plugin.mailbox().grant(target.getUniqueId(), reward, plugin.dayClock().today())
                .whenComplete((v, ex) -> plugin.runMain(() -> {
                    if (ex != null) {
                        sender.sendMessage(Component.text(
                                "Failed to grant reward to " + target.getName() + ": " + ex, NamedTextColor.RED));
                        return;
                    }
                    sender.sendMessage(Component.text(
                            "Granted reward to " + target.getName() + "'s mailbox.", NamedTextColor.GREEN));
                }));
    }

    /**
     * Resolves a {@code grant} reward id: first a task-pool entry by id (granting that task's
     * reward), otherwise a raw {@code MATERIAL:amount;…} payload decoded by {@link RewardCodec}.
     * Returns {@code null} if it is neither.
     */
    private ItemReward resolveReward(String rewardId) {
        for (RawTask task : plugin.config().tasks().pool()) {
            if (task.id().equalsIgnoreCase(rewardId)) {
                return ItemReward.of(task.reward());
            }
        }
        try {
            ItemReward decoded = RewardCodec.decode(rewardId);
            return decoded.items().isEmpty() ? null : decoded;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // --- Admin: reset ----------------------------------------------------------------------

    private void adminReset(CommandSender sender, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(Component.text(
                    "Player '" + targetName + "' is not online; reset targets an online player.",
                    NamedTextColor.RED));
            return;
        }
        plugin.stateDao().put(PlayerState.zeroed(target.getUniqueId()))
                .whenComplete((v, ex) -> plugin.runMain(() -> {
                    if (ex != null) {
                        sender.sendMessage(Component.text(
                                "Failed to reset " + target.getName() + ": " + ex, NamedTextColor.RED));
                        return;
                    }
                    sender.sendMessage(Component.text(
                            "Reset streak state for " + target.getName()
                                    + " (today's task progress is left intact).",
                            NamedTextColor.GREEN));
                }));
    }

    // --- Usage -----------------------------------------------------------------------------

    private void sendUsage(CommandSender sender, boolean isAdmin) {
        sender.sendMessage(Component.text("DailyQ commands:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /daily            - open the DailyQ hub", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /daily tasks      - today's tasks", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /daily streak     - your login streak", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /daily claim      - open your reward mailbox", NamedTextColor.GRAY));
        if (isAdmin) {
            sender.sendMessage(Component.text("  /daily admin reload", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /daily admin grant <player> <rewardId>", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /daily admin reset <player>", NamedTextColor.GRAY));
        }
    }

    // --- Tab completion --------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean isAdmin = sender.hasPermission("dailyq.admin");
        if (args.length == 1) {
            List<String> roots = new ArrayList<>(List.of("tasks", "streak", "claim"));
            if (isAdmin) {
                roots.add("admin");
            }
            return filter(roots, args[0]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("admin") && isAdmin) {
            if (args.length == 2) {
                return filter(List.of("reload", "grant", "reset"), args[1]);
            }
            if (args.length == 3 && (args[1].equalsIgnoreCase("grant") || args[1].equalsIgnoreCase("reset"))) {
                return filter(onlinePlayerNames(), args[2]);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("grant")) {
                return filter(taskPoolIds(), args[3]);
            }
        }
        return List.of();
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> taskPoolIds() {
        List<String> ids = new ArrayList<>();
        for (RawTask task : plugin.config().tasks().pool()) {
            ids.add(task.id());
        }
        return ids;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
