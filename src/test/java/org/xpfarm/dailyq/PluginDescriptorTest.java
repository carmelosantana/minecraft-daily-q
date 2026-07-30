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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Parses the shipped resource YAML with the same SnakeYAML the server uses.
 *
 * <h2>Why this exists</h2>
 *
 * <p>A malformed {@code plugin.yml} is not a compile error, is not a test failure, and does not
 * fail {@code mvn verify} — Maven copies the file into the JAR and it is only parsed when a real
 * Paper server boots. A descriptor that will not parse makes Paper skip the plugin entirely
 * rather than load it disabled, a materially more confusing symptom. These tests close that gap
 * at gate 6 instead of gate 7a.
 *
 * <p>minecraft-plugin-dev tightens the command/permission assertions to what the code actually
 * looks up as it writes that code.
 */
final class PluginDescriptorTest {

    private static final Path PLUGIN_YML = descriptor("plugin.yml");
    private static final Path CONFIG_YML = descriptor("config.yml");

    /**
     * Prefers the Maven-filtered copy in {@code target/classes} — that is the file that actually
     * ships, and property substitution can inject YAML metacharacters the source file never had.
     * Falls back to the source tree so the test still runs before {@code process-resources}.
     */
    private static Path descriptor(String name) {
        Path filtered = Path.of("target", "classes", name);
        return Files.exists(filtered) ? filtered : Path.of("src", "main", "resources", name);
    }

    private static Map<String, Object> parse(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return new Yaml().load(in);
        }
    }

    @Test
    void pluginYmlIsValidYaml() throws IOException {
        assertNotNull(parse(PLUGIN_YML), "plugin.yml parsed to null — the file is empty or malformed");
    }

    @Test
    void configYmlIsValidYaml() throws IOException {
        assertNotNull(parse(CONFIG_YML), "config.yml parsed to null — the file is empty or malformed");
    }

    @Test
    void pluginYmlDeclaresTheFieldsPaperRequires() throws IOException {
        Map<String, Object> parsed = parse(PLUGIN_YML);

        assertEquals("DailyQ", parsed.get("name"));
        assertEquals("org.xpfarm.dailyq.DailyQPlugin", parsed.get("main"));
        assertInstanceOf(String.class, parsed.get("api-version"),
                "api-version must be quoted; unquoted it parses as a double and 1.20 becomes 1.2");
        assertEquals("26.1", parsed.get("api-version"));
        assertNotNull(parsed.get("description"), "description is required");

        Object version = parsed.get("version");
        assertNotNull(version, "version is required");
        assertFalse(version.toString().contains("${"),
                "version still holds an unresolved Maven property: " + version);
    }

    @Test
    void pluginYmlDeclaresEveryCommandTheCodeLooksUp() throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> commands = (Map<String, Object>) parse(PLUGIN_YML).get("commands");
        assertNotNull(commands, "commands section is required");
        assertTrue(commands.containsKey("daily"),
                "the daily command must be declared or getCommand(\"daily\") returns null");
    }

    @Test
    void pluginYmlDeclaresEveryPermissionTheCodeChecks() throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> permissions = (Map<String, Object>) parse(PLUGIN_YML).get("permissions");
        assertNotNull(permissions, "permissions section is required");
        assertTrue(permissions.containsKey("dailyq.use"), "dailyq.use must be declared");
        assertTrue(permissions.containsKey("dailyq.admin"), "dailyq.admin must be declared");
    }

    @Test
    void pluginYmlDeclaresItsSoftDependencies() throws IOException {
        Object softdepend = parse(PLUGIN_YML).get("softdepend");
        assertNotNull(softdepend, "softdepend is required");
        String declared = softdepend.toString();
        assertTrue(declared.contains("floodgate"), "floodgate must be a soft dependency");
    }

    @Test
    void pluginYmlLibraryVersionMatchesPom() throws IOException {
        Object libraries = parse(PLUGIN_YML).get("libraries");
        assertNotNull(libraries, "libraries is required — the SQLite driver is loaded at runtime");
        assertTrue(libraries.toString().contains("org.xerial:sqlite-jdbc:3.53.2.0"),
                "the plugin.yml sqlite-jdbc coordinate must match the pom.xml version so the two cannot drift");
    }
}
