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
 * One unit of progress produced from a Bukkit event.
 *
 * <p>{@code target} is a plain string so {@link ProgressEvaluator} stays Bukkit-free: the listener
 * (Task 10) is responsible for turning a block/item/entity into the right string — a
 * {@code Material} or {@code EntityType} name, or for {@code KILL}, the mob category
 * ({@code HOSTILE}/{@code PASSIVE}) the killed entity falls into.
 *
 * @param archetype the archetype the triggering event maps onto
 * @param target the block, item, or mob-category string the event produced
 * @param amount how much progress this signal is worth
 */
public record ProgressSignal(TaskArchetype archetype, String target, int amount) {
}
