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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.xpfarm.dailyq.streak.StreakOutcome;
import org.xpfarm.dailyq.task.TaskDefinition;

/**
 * Builds the "today" join card: streak, today's task set with progress, and a claim hint.
 *
 * <p>Pure text-component construction — no Bukkit server, no Floodgate, nothing platform-specific
 * at all. This is deliberate: {@link org.xpfarm.dailyq.ui.DailyUi} decides <em>where</em> a
 * player's UI goes (chest inventory vs. Cumulus form), but the join card itself is chat text sent
 * identically to every player regardless of platform, so it needs none of that routing and stays
 * exhaustively unit-testable by asserting on the serialized plain text of the {@link Component} it
 * returns.
 *
 * <p>{@code streak} is accepted as its own parameter rather than read off {@code outcome} because
 * {@link StreakOutcome} alone cannot distinguish a brand-new player's first-ever login from a
 * streak that has stepped back down to 1 after a missed-day forgiveness window expires — both
 * produce an identical {@code StreakOutcome(1, true, false, false)}. The caller (which does have
 * the player's login history) decides what streak number to display; this class only decides how
 * to word it, treating {@code streak <= 1} (and not already counted today) as "day 1" wording for
 * either case, since both really are "you are starting (or restarting) a streak at day 1."
 */
public final class TodayMessage {

    private TodayMessage() {
    }

    /**
     * Renders the today card for {@code playerName}.
     *
     * @param playerName the player's display name
     * @param outcome    the streak outcome computed for this login
     * @param streak     the streak number to display (see class docs for why this is separate
     *                   from {@code outcome.newStreak()})
     * @param today      today's task selection, in display order
     * @param progress   each task's current progress, keyed by {@link TaskDefinition#id()}; a
     *                   task missing from this map is treated as zero progress
     * @return the rendered card, ready to send as a chat message
     */
    public static Component render(
            String playerName, StreakOutcome outcome, int streak, List<TaskDefinition> today,
            Map<String, Integer> progress) {
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(progress, "progress");

        Component message = Component.text("DailyQ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text(greeting(playerName, outcome, streak), NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text(streakLine(outcome, streak), NamedTextColor.AQUA))
                .append(Component.newline());

        if (!today.isEmpty()) {
            message = message.append(Component.text("Today's tasks:", NamedTextColor.WHITE))
                    .append(Component.newline());
            for (TaskDefinition task : today) {
                int done = progress.getOrDefault(task.id(), 0);
                message = message.append(Component.text(" - " + taskLine(task, done), NamedTextColor.GRAY))
                        .append(Component.newline());
            }
        }

        return message.append(Component.text(claimHint(outcome), NamedTextColor.GREEN));
    }

    private static String greeting(String playerName, StreakOutcome outcome, int streak) {
        if (outcome.alreadyCountedToday()) {
            return "Welcome back, " + playerName + "! You already checked in today.";
        }
        if (streak <= 1) {
            return "Welcome, " + playerName + "! This is day 1 of your streak.";
        }
        return "Welcome back, " + playerName + "!";
    }

    private static String streakLine(StreakOutcome outcome, int streak) {
        String line = "Streak: Day " + streak;
        if (outcome.makeUpAvailable()) {
            line += " (streak make-up available)";
        }
        return line;
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

    private static String claimHint(StreakOutcome outcome) {
        if (outcome.streakRewardClaimable()) {
            return "Open your mailbox to claim today's streak reward!";
        }
        return "Open your mailbox to claim any pending rewards!";
    }
}
