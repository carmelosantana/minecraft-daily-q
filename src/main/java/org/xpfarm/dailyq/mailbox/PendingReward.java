/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.mailbox;

import org.xpfarm.dailyq.reward.ItemReward;

/**
 * One unclaimed mailbox entry, decoded and ready to hand to a caller.
 *
 * @param id     the underlying {@code mailbox} row id, needed to claim this specific entry
 * @param reward the decoded reward
 */
public record PendingReward(long id, ItemReward reward) {
}
