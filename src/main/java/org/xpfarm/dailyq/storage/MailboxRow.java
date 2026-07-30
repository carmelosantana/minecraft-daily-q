/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.storage;

/**
 * One row of the {@code mailbox} table, as read by {@link MailboxDao#listUnclaimed}.
 *
 * @param id         the row's auto-incrementing id
 * @param reward     the raw, codec-encoded reward string
 * @param createdDay the server day the entry was created on
 */
public record MailboxRow(long id, String reward, long createdDay) {
}
