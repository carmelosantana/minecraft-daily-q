/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.xpfarm.dailyq.reward.ItemSpec;

/**
 * {@link ConfigParser} is exercised entirely through {@link YamlConfiguration}, which is plain
 * SnakeYAML-backed Bukkit configuration code — it needs no running server, keeping this test
 * fast and server-free.
 */
final class ConfigParserTest {

    /**
     * Mirrors the shape of the shipped {@code src/main/resources/config.yml} so the mutation
     * tests below can each break exactly one thing.
     */
    private static final String VALID_YAML =
            """
            reset:
              hour: 0
              timezone: 'UTC'
            tasks:
              per-day: 3
              completion-bonus:
                items:
                  - { material: DIAMOND, amount: 2 }
              pool:
                - { id: mine_iron, archetype: MINE, target: IRON_ORE, count: 32, tier: easy,
                    reward: { items: [ { material: BREAD, amount: 4 } ] } }
                - { id: harvest_wheat, archetype: HARVEST, target: WHEAT, count: 64, tier: medium,
                    reward: { items: [ { material: EXPERIENCE_BOTTLE, amount: 4 } ] } }
                - { id: kill_hostiles, archetype: KILL, target: HOSTILE, count: 20, tier: stretch,
                    reward: { items: [ { material: DIAMOND, amount: 1 } ] } }
            login:
              milestone-days: [7, 14, 28]
              calendar:
                - { day: 1, reward: { items: [ { material: BREAD, amount: 2 } ] } }
                - { day: 7, reward: { items: [ { material: DIAMOND, amount: 3 } ] } }
                - { day: 14, reward: { items: [ { material: IRON_BLOCK, amount: 2 } ] } }
                - { day: 28, reward: { items: [ { material: DIAMOND_BLOCK, amount: 1 } ] } }
            streak:
              forgiveness-misses: 1
              make-up-enabled: true
            messages:
              today-card: true
              toast: true
            storage:
              db-file: 'daily-q.db'
              busy-timeout-ms: 5000
            """;

    private static ConfigurationSection sectionOf(String yaml) {
        return YamlConfiguration.loadConfiguration(new StringReader(yaml));
    }

    @Test
    void shippedConfigYmlParsesToExpectedRecordGraph() throws IOException, ConfigException {
        Path configFile = Path.of("src", "main", "resources", "config.yml");
        ConfigurationSection root =
                YamlConfiguration.loadConfiguration(Files.newBufferedReader(configFile));

        DailyQConfig config = ConfigParser.parse(root);

        assertEquals(0, config.reset().hour());
        assertEquals(ZoneId.of("UTC"), config.reset().zone());

        assertEquals(3, config.tasks().perDay());
        assertEquals(List.of(new ItemSpec(Material.DIAMOND, 2)), config.tasks().completionBonus());
        assertEquals(3, config.tasks().pool().size());
        Set<String> tiers =
                config.tasks().pool().stream().map(DailyQConfig.RawTask::tier).collect(Collectors.toSet());
        assertEquals(Set.of("easy", "medium", "stretch"), tiers);

        assertEquals(Set.of(7, 14, 28), config.login().milestoneDays());
        assertEquals(4, config.login().calendar().size());
        assertEquals(
                List.of(new ItemSpec(Material.DIAMOND_BLOCK, 1)), config.login().calendar().get(28));

        assertEquals(1, config.streak().forgivenessMisses());
        assertTrue(config.streak().makeUpEnabled());

        assertTrue(config.messages().todayCard());
        assertTrue(config.messages().toast());

        assertEquals("daily-q.db", config.storage().dbFile());
        assertEquals(5000, config.storage().busyTimeoutMs());
    }

    @Test
    void perDayZeroThrows() {
        String broken = VALID_YAML.replace("per-day: 3", "per-day: 0");
        ConfigException e =
                assertThrows(ConfigException.class, () -> ConfigParser.parse(sectionOf(broken)));
        assertTrue(e.getMessage().contains("per-day"), "message should name the bad key: " + e.getMessage());
    }

    @Test
    void unknownMaterialInRewardThrowsWithKeyInMessage() {
        String broken = VALID_YAML.replace("material: DIAMOND, amount: 2", "material: NOT_A_MATERIAL, amount: 2");
        ConfigException e =
                assertThrows(ConfigException.class, () -> ConfigParser.parse(sectionOf(broken)));
        assertTrue(
                e.getMessage().contains("NOT_A_MATERIAL"),
                "message should name the bad material: " + e.getMessage());
    }

    @Test
    void unknownTimezoneThrows() {
        String broken = VALID_YAML.replace("timezone: 'UTC'", "timezone: 'Not/AZone'");
        ConfigException e =
                assertThrows(ConfigException.class, () -> ConfigParser.parse(sectionOf(broken)));
        assertTrue(
                e.getMessage().contains("Not/AZone"),
                "message should name the bad timezone: " + e.getMessage());
    }

    @Test
    void serverTimezoneResolvesToSystemDefault() throws ConfigException {
        String yaml = VALID_YAML.replace("timezone: 'UTC'", "timezone: 'server'");
        DailyQConfig config = ConfigParser.parse(sectionOf(yaml));
        assertEquals(ZoneId.systemDefault(), config.reset().zone());
    }

    @Test
    void poolMissingStretchTierThrows() {
        String broken =
                VALID_YAML.replace(
                        "id: kill_hostiles, archetype: KILL, target: HOSTILE, count: 20, tier: stretch",
                        "id: kill_hostiles, archetype: KILL, target: HOSTILE, count: 20, tier: medium");
        ConfigException e =
                assertThrows(ConfigException.class, () -> ConfigParser.parse(sectionOf(broken)));
        assertTrue(e.getMessage().contains("stretch"), "message should name the missing tier: " + e.getMessage());
    }
}
