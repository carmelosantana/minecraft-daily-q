/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.task;

/**
 * The pure event-to-progress mapping the listener (Task 10) feeds Bukkit events into.
 *
 * <p>Kept free of any Bukkit type so the archetype/target matching rules are unit-testable without
 * a running server; the listener alone is responsible for turning a Bukkit event into the right
 * {@link ProgressSignal} (which {@code Material}/{@code EntityType} name, or which {@code KILL}
 * mob category, a given event corresponds to).
 */
public final class ProgressEvaluator {

    /** A {@link TaskDefinition#target()} of this value matches any signal target. */
    private static final String WILDCARD_ANY = "ANY";

    private ProgressEvaluator() {
    }

    /**
     * Returns how much progress {@code signal} contributes to {@code def}.
     *
     * <p>{@code signal} counts toward {@code def} only when its archetype matches exactly and its
     * target matches {@code def.target()} — either literally (case-insensitive), or because
     * {@code def.target()} is the {@code ANY} wildcard. This also covers the {@code KILL}
     * {@code HOSTILE}/{@code PASSIVE} category match: a def targeting {@code HOSTILE} matches a
     * signal whose target string is {@code HOSTILE}, by the same literal comparison.
     *
     * @return {@code signal.amount()} on a match, {@code 0} otherwise
     */
    public static int increment(TaskDefinition def, ProgressSignal signal) {
        if (def.archetype() != signal.archetype()) {
            return 0;
        }
        if (!targetMatches(def.target(), signal.target())) {
            return 0;
        }
        return signal.amount();
    }

    private static boolean targetMatches(String defTarget, String signalTarget) {
        if (defTarget == null || signalTarget == null) {
            return false;
        }
        if (WILDCARD_ANY.equalsIgnoreCase(defTarget)) {
            return true;
        }
        return defTarget.equalsIgnoreCase(signalTarget);
    }
}
