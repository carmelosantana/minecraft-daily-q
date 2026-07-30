/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.reward;

import org.bukkit.Material;

/**
 * A single reward line: a {@link Material} and the amount granted.
 *
 * <p>{@link org.xpfarm.dailyq.config.ConfigParser} constructs these only after validating that
 * {@code amount} is at least 1, so every {@code ItemSpec} in a parsed
 * {@link org.xpfarm.dailyq.config.DailyQConfig} is already known-good.
 */
public record ItemSpec(Material material, int amount) {
}
