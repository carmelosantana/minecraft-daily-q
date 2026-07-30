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

import java.util.Locale;

/**
 * The pure {@code /daily} routing + permission decision, free of any Bukkit type.
 *
 * <p>{@link DailyCommand} is a thin adapter: it resolves {@code isPlayer}/{@code isAdmin} from the
 * live {@code CommandSender} and then does exactly what the {@link Route} this returns says. Keeping
 * the decision here (rather than tangled into the executor) is what makes it exhaustively
 * unit-testable without a running server, and is what makes the command dispatch-safe — the same
 * argument vector produces the same {@link Route} whether a player typed it or another plugin
 * dispatched it with {@code run-as: player} (see design spec §13).
 *
 * <p>Player views ({@link Kind#HUB}, {@link Kind#TASKS}, {@link Kind#STREAK}, {@link Kind#CLAIM})
 * require a player sender; a console sender routes to {@link Kind#DENY_CONSOLE}. The {@code admin}
 * subtree requires {@code dailyq.admin}; without it every {@code admin ...} form routes to
 * {@link Kind#DENY_ADMIN} (defense in depth on top of Pizza's allowlist). {@code admin reload} does
 * NOT require a player sender — it is a config reload an operator may run from console.
 */
public final class DailyCommandRouter {

    /** The decision {@link #route} makes for one {@code /daily} invocation. */
    public enum Kind {
        /** No-arg {@code /daily}: open the hub. */
        HUB,
        /** {@code /daily tasks}: open today's task set. */
        TASKS,
        /** {@code /daily streak}: open the streak view. */
        STREAK,
        /** {@code /daily claim}: open the reward mailbox. */
        CLAIM,
        /** {@code /daily admin reload}: re-parse and re-apply the configuration. */
        ADMIN_RELOAD,
        /** {@code /daily admin grant <player> <rewardId>}: grant a reward to a player's mailbox. */
        ADMIN_GRANT,
        /** {@code /daily admin reset <player>}: reset a player's streak/daily state. */
        ADMIN_RESET,
        /** A player subcommand was run by a non-player (console) sender. */
        DENY_CONSOLE,
        /** An {@code admin} subcommand was run without {@code dailyq.admin}. */
        DENY_ADMIN,
        /** The arguments did not form a recognized command; show usage. */
        USAGE
    }

    /**
     * The resolved route.
     *
     * @param kind         what to do
     * @param targetPlayer for {@link Kind#ADMIN_GRANT}/{@link Kind#ADMIN_RESET}, the named player;
     *                     {@code null} otherwise
     * @param rewardId     for {@link Kind#ADMIN_GRANT}, the reward identifier; {@code null}
     *                     otherwise
     */
    public record Route(Kind kind, String targetPlayer, String rewardId) {

        static Route of(Kind kind) {
            return new Route(kind, null, null);
        }
    }

    private DailyCommandRouter() {
    }

    /**
     * Maps a parsed argument vector and the sender's capabilities to a {@link Route}.
     *
     * @param args     the command arguments after {@code /daily}
     * @param isPlayer whether the sender is a player (vs. console)
     * @param isAdmin  whether the sender holds {@code dailyq.admin}
     * @return the route to act on; never {@code null}
     */
    public static Route route(String[] args, boolean isPlayer, boolean isAdmin) {
        if (args == null || args.length == 0) {
            return isPlayer ? Route.of(Kind.HUB) : Route.of(Kind.DENY_CONSOLE);
        }

        String head = args[0].toLowerCase(Locale.ROOT);
        return switch (head) {
            case "tasks" -> playerView(Kind.TASKS, isPlayer);
            case "streak" -> playerView(Kind.STREAK, isPlayer);
            case "claim" -> playerView(Kind.CLAIM, isPlayer);
            case "admin" -> admin(args, isAdmin);
            default -> Route.of(Kind.USAGE);
        };
    }

    private static Route playerView(Kind kind, boolean isPlayer) {
        return isPlayer ? Route.of(kind) : Route.of(Kind.DENY_CONSOLE);
    }

    private static Route admin(String[] args, boolean isAdmin) {
        if (!isAdmin) {
            return Route.of(Kind.DENY_ADMIN);
        }
        if (args.length < 2) {
            return Route.of(Kind.USAGE);
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "reload" -> Route.of(Kind.ADMIN_RELOAD);
            case "grant" -> args.length >= 4
                    ? new Route(Kind.ADMIN_GRANT, args[2], args[3])
                    : Route.of(Kind.USAGE);
            case "reset" -> args.length >= 3
                    ? new Route(Kind.ADMIN_RESET, args[2], null)
                    : Route.of(Kind.USAGE);
            default -> Route.of(Kind.USAGE);
        };
    }
}
