/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.streak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xpfarm.dailyq.storage.PlayerState;

/**
 * Exhaustive, table-driven coverage of {@link StreakService#computeOnJoin}, matching the exact
 * branch structure in the task-5 brief: same-day short-circuit, first-ever login, consecutive
 * login, forgiven miss with optional make-up, and step-down beyond the forgiveness window
 * (floored at 1).
 */
final class StreakServiceTest {

    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static PlayerState state(int streak, long lastLoginDay, boolean makeUpUsed) {
        return new PlayerState(PLAYER, streak, lastLoginDay, makeUpUsed, -1L);
    }

    // --- Individual scenarios named directly after the brief's test list -----------------

    @Test
    void firstLoginEverStartsStreakAtOneAndIsClaimable() {
        StreakService service = new StreakService(1, true, 28);
        PlayerState firstEver = state(0, -1L, false);

        StreakOutcome outcome = service.computeOnJoin(firstEver, 100L);

        assertEquals(new StreakOutcome(1, true, false, false), outcome);
    }

    @Test
    void consecutiveDayIncrementsStreak() {
        StreakService service = new StreakService(1, true, 28);
        PlayerState state = state(5, 10L, false);

        StreakOutcome outcome = service.computeOnJoin(state, 11L);

        assertEquals(new StreakOutcome(6, true, false, false), outcome);
    }

    @Test
    void sameDayAgainMarksAlreadyCountedAndNotClaimable() {
        StreakService service = new StreakService(1, true, 28);
        PlayerState state = state(5, 10L, false);

        StreakOutcome outcome = service.computeOnJoin(state, 10L);

        assertEquals(new StreakOutcome(5, false, false, true), outcome);
    }

    @Test
    void oneMissedDayWithinForgivenessHoldsStreakAndOffersMakeUpWhenUnused() {
        StreakService service = new StreakService(1, true, 28);
        PlayerState state = state(5, 10L, false);

        // missed = (today - lastLoginDay) - 1 = (12 - 10) - 1 = 1, forgivenessMisses = 1
        StreakOutcome outcome = service.computeOnJoin(state, 12L);

        assertEquals(new StreakOutcome(5, true, true, false), outcome);
    }

    @Test
    void oneMissedDayWithinForgivenessNoMakeUpWhenAlreadyUsed() {
        StreakService service = new StreakService(1, true, 28);
        PlayerState state = state(5, 10L, true);

        StreakOutcome outcome = service.computeOnJoin(state, 12L);

        assertEquals(new StreakOutcome(5, true, false, false), outcome);
    }

    @Test
    void oneMissedDayWithinForgivenessNoMakeUpWhenMakeUpDisabled() {
        StreakService service = new StreakService(1, false, 28);
        PlayerState state = state(5, 10L, false);

        StreakOutcome outcome = service.computeOnJoin(state, 12L);

        assertEquals(new StreakOutcome(5, true, false, false), outcome);
    }

    @Test
    void twoMissedDaysExceedsForgivenessStepsStreakDown() {
        StreakService service = new StreakService(1, true, 28);
        PlayerState state = state(5, 10L, false);

        // missed = (13 - 10) - 1 = 2, forgivenessMisses = 1 -> step down
        StreakOutcome outcome = service.computeOnJoin(state, 13L);

        assertEquals(new StreakOutcome(4, true, false, false), outcome);
    }

    @Test
    void stepDownFromStreakOneStaysAtOne() {
        StreakService service = new StreakService(1, true, 28);
        PlayerState state = state(1, 10L, false);

        StreakOutcome outcome = service.computeOnJoin(state, 13L);

        assertEquals(new StreakOutcome(1, true, false, false), outcome);
    }

    @Test
    void firstLoginEverIgnoresAnyStalePriorStreakValue() {
        StreakService service = new StreakService(1, true, 28);
        // lastLoginDay < 0 always means "first ever", regardless of whatever streak happens
        // to be sitting on the record (defensive: PlayerState.zeroed() uses streak 0, but the
        // algorithm must force 1 even if some other stale value were present).
        PlayerState staleFirstEver = state(9, -1L, false);

        StreakOutcome outcome = service.computeOnJoin(staleFirstEver, 500L);

        assertEquals(1, outcome.newStreak());
        assertTrue(outcome.streakRewardClaimable());
        assertFalse(outcome.makeUpAvailable());
        assertFalse(outcome.alreadyCountedToday());
    }

    @Test
    void makeUpNeverOfferedOnConsecutiveDayEvenWhenEnabledAndUnused() {
        StreakService service = new StreakService(1, true, 28);
        PlayerState state = state(5, 10L, false);

        StreakOutcome outcome = service.computeOnJoin(state, 11L);

        assertFalse(outcome.makeUpAvailable());
    }

    @Test
    void makeUpNeverOfferedOnStepDownEvenWhenEnabledAndUnused() {
        StreakService service = new StreakService(1, true, 28);
        PlayerState state = state(5, 10L, false);

        StreakOutcome outcome = service.computeOnJoin(state, 13L);

        assertFalse(outcome.makeUpAvailable());
    }

    @Test
    void makeUpNeverOfferedOnFirstLoginEvenWhenEnabledAndUnused() {
        StreakService service = new StreakService(1, true, 28);
        PlayerState state = state(0, -1L, false);

        StreakOutcome outcome = service.computeOnJoin(state, 100L);

        assertFalse(outcome.makeUpAvailable());
    }

    // --- Table-driven matrix over the missed/forgiveness/streak decision surface ----------

    private static Stream<Arguments> streakTransitionCases() {
        return Stream.of(
                // description, missedDays, forgivenessMisses, priorStreak, expectedNewStreak
                Arguments.of("consecutive (missed=0)", 0L, 1, 5, 6),
                Arguments.of("held: missed==forgiveness (1<=1)", 1L, 1, 5, 5),
                Arguments.of("step down: missed>forgiveness (1>0)", 1L, 0, 5, 4),
                Arguments.of("step down: missed>forgiveness (2>1)", 2L, 1, 5, 4),
                Arguments.of("held: missed==forgiveness (2<=2)", 2L, 2, 5, 5),
                Arguments.of("step down: missed>forgiveness (3>2)", 3L, 2, 5, 4),
                Arguments.of("held keeps streak of 1 at 1", 2L, 2, 1, 1),
                Arguments.of("step down floors at 1 from streak 1", 5L, 1, 1, 1),
                Arguments.of("step down floors at 1 from streak 2", 5L, 1, 2, 1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("streakTransitionCases")
    void computeOnJoinStreakTransitionMatrix(
            String description, long missedDays, int forgivenessMisses, int priorStreak, int expectedNewStreak) {
        StreakService service = new StreakService(forgivenessMisses, true, 28);
        long lastLoginDay = 10L;
        long today = lastLoginDay + missedDays + 1;
        PlayerState state = state(priorStreak, lastLoginDay, false);

        StreakOutcome outcome = service.computeOnJoin(state, today);

        assertEquals(expectedNewStreak, outcome.newStreak(), description);
        assertTrue(outcome.streakRewardClaimable(), description + " should always be claimable (not same-day)");
        assertFalse(outcome.alreadyCountedToday(), description);
    }
}
