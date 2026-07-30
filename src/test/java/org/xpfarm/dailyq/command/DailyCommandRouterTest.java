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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.xpfarm.dailyq.command.DailyCommandRouter.Kind;
import org.xpfarm.dailyq.command.DailyCommandRouter.Route;

/**
 * Exhaustive coverage of {@link DailyCommandRouter#route}: every argument shape crossed with the
 * player/console and admin/non-admin sender axes, so the executor can stay a thin adapter and the
 * whole routing + permission decision is verified without a running server.
 */
final class DailyCommandRouterTest {

    private static final boolean PLAYER = true;
    private static final boolean CONSOLE = false;
    private static final boolean ADMIN = true;
    private static final boolean NOT_ADMIN = false;

    private static Route route(String[] args, boolean isPlayer, boolean isAdmin) {
        return DailyCommandRouter.route(args, isPlayer, isAdmin);
    }

    private static String[] args(String... a) {
        return a;
    }

    // --- No-arg hub -----------------------------------------------------------------------

    @Test
    void noArgsFromPlayerOpensHub() {
        assertEquals(Kind.HUB, route(args(), PLAYER, NOT_ADMIN).kind());
    }

    @Test
    void noArgsFromConsoleIsRejected() {
        assertEquals(Kind.DENY_CONSOLE, route(args(), CONSOLE, ADMIN).kind());
    }

    @Test
    void nullArgsFromPlayerOpensHub() {
        assertEquals(Kind.HUB, route(null, PLAYER, NOT_ADMIN).kind());
    }

    // --- Player views ---------------------------------------------------------------------

    @Test
    void tasksFromPlayerRoutesToTasks() {
        assertEquals(Kind.TASKS, route(args("tasks"), PLAYER, NOT_ADMIN).kind());
    }

    @Test
    void streakFromPlayerRoutesToStreak() {
        assertEquals(Kind.STREAK, route(args("streak"), PLAYER, NOT_ADMIN).kind());
    }

    @Test
    void claimFromPlayerRoutesToClaim() {
        assertEquals(Kind.CLAIM, route(args("claim"), PLAYER, NOT_ADMIN).kind());
    }

    @Test
    void playerViewsAreCaseInsensitive() {
        assertEquals(Kind.TASKS, route(args("TASKS"), PLAYER, NOT_ADMIN).kind());
        assertEquals(Kind.CLAIM, route(args("Claim"), PLAYER, NOT_ADMIN).kind());
    }

    @Test
    void playerViewsFromConsoleAreRejected() {
        assertEquals(Kind.DENY_CONSOLE, route(args("tasks"), CONSOLE, ADMIN).kind());
        assertEquals(Kind.DENY_CONSOLE, route(args("streak"), CONSOLE, ADMIN).kind());
        assertEquals(Kind.DENY_CONSOLE, route(args("claim"), CONSOLE, ADMIN).kind());
    }

    // --- Admin permission gating ----------------------------------------------------------

    @Test
    void adminWithoutAdminPermIsDenied() {
        assertEquals(Kind.DENY_ADMIN, route(args("admin", "reload"), PLAYER, NOT_ADMIN).kind());
        assertEquals(Kind.DENY_ADMIN, route(args("admin", "grant", "bob", "r"), PLAYER, NOT_ADMIN).kind());
        assertEquals(Kind.DENY_ADMIN, route(args("admin", "reset", "bob"), CONSOLE, NOT_ADMIN).kind());
        assertEquals(Kind.DENY_ADMIN, route(args("admin"), PLAYER, NOT_ADMIN).kind());
    }

    // --- Admin reload ---------------------------------------------------------------------

    @Test
    void adminReloadFromAdminPlayerRoutes() {
        assertEquals(Kind.ADMIN_RELOAD, route(args("admin", "reload"), PLAYER, ADMIN).kind());
    }

    @Test
    void adminReloadFromAdminConsoleRoutes() {
        // reload is not a player view: an operator may run it from console.
        assertEquals(Kind.ADMIN_RELOAD, route(args("admin", "reload"), CONSOLE, ADMIN).kind());
    }

    @Test
    void adminReloadIsCaseInsensitive() {
        assertEquals(Kind.ADMIN_RELOAD, route(args("Admin", "Reload"), PLAYER, ADMIN).kind());
    }

    // --- Admin grant ----------------------------------------------------------------------

    @Test
    void adminGrantCapturesPlayerAndReward() {
        Route r = route(args("admin", "grant", "steve", "mine_iron"), PLAYER, ADMIN);
        assertEquals(Kind.ADMIN_GRANT, r.kind());
        assertEquals("steve", r.targetPlayer());
        assertEquals("mine_iron", r.rewardId());
    }

    @Test
    void adminGrantMissingRewardIsUsage() {
        assertEquals(Kind.USAGE, route(args("admin", "grant", "steve"), PLAYER, ADMIN).kind());
    }

    @Test
    void adminGrantMissingPlayerIsUsage() {
        assertEquals(Kind.USAGE, route(args("admin", "grant"), PLAYER, ADMIN).kind());
    }

    // --- Admin reset ----------------------------------------------------------------------

    @Test
    void adminResetCapturesPlayer() {
        Route r = route(args("admin", "reset", "alex"), CONSOLE, ADMIN);
        assertEquals(Kind.ADMIN_RESET, r.kind());
        assertEquals("alex", r.targetPlayer());
        assertNull(r.rewardId());
    }

    @Test
    void adminResetMissingPlayerIsUsage() {
        assertEquals(Kind.USAGE, route(args("admin", "reset"), PLAYER, ADMIN).kind());
    }

    // --- Unknown ---------------------------------------------------------------------------

    @Test
    void unknownSubcommandIsUsage() {
        assertEquals(Kind.USAGE, route(args("wat"), PLAYER, ADMIN).kind());
    }

    @Test
    void unknownAdminSubcommandIsUsage() {
        assertEquals(Kind.USAGE, route(args("admin", "wat"), PLAYER, ADMIN).kind());
    }
}
