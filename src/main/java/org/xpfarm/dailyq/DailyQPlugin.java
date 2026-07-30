/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * DailyQ plugin entry point.
 *
 * <p>Scaffold stub. minecraft-plugin-dev (gate 4) wires the task engine, streak engine, reward
 * mailbox, UI, persistence, and the {@code /daily} command surface here per the design spec at
 * {@code docs/superpowers/specs/2026-07-30-daily-q-design.md}. It saves the default config so the
 * bundled {@code config.yml} lands on disk on first run.
 */
public final class DailyQPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
    }
}
