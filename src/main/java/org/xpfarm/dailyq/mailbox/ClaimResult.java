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

/**
 * The outcome of one {@link MailboxService#claim} call.
 *
 * @param claimedCount   rows fully delivered (empty leftovers) and marked claimed this call
 * @param remainingCount rows still unclaimed after this call, either because delivery left
 *                       leftovers or because the row wasn't attempted
 */
public record ClaimResult(int claimedCount, int remainingCount) {
}
