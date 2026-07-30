/*
 * DailyQ - daily quests, login-streak rewards, and a claim mailbox for xpfarm.org.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.dailyq.bedrock;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntConsumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;

/**
 * The single seam between DailyQ and Floodgate/Cumulus.
 *
 * <p>This is the ONLY class in the plugin permitted to import {@code org.geysermc.*}. Every other
 * class — including {@link org.xpfarm.dailyq.ui.DailyUi}, which decides whether a given player
 * gets a Bedrock form or a Java chest — only ever touches this class through {@link UUID}/{@link
 * Player}/JDK-functional-interface signatures ({@link #isBedrock}, {@link #openForm}); none of
 * those signatures name a Geyser or Cumulus type, so nothing outside this file forces the JVM to
 * resolve those classes.
 *
 * <p>{@link #createIfAvailable()} is the only place this class is ever constructed, and it is
 * guarded twice: first by {@link org.bukkit.plugin.PluginManager#isPluginEnabled(String)}
 * ("floodgate"), then by a {@code try/catch} around the {@link FloodgateApi#getInstance()} lookup
 * itself. A server without Floodgate installed at all never even links this class's {@code
 * org.geysermc.*} imports, because the {@code new FloodgateBridge(...)} branch is textually
 * present but never executes when the plugin check fails — JVM class loading is lazy. A server
 * that has Floodgate enabled but whose API lookup throws for any other reason (version mismatch,
 * not fully initialized yet, etc.) falls back the same way: {@link Optional#empty()}, so the
 * caller holds an {@code Optional<FloodgateBridge>} and defaults every player to the Java chest
 * path.
 *
 * <p>{@link #isBedrock} uses {@link FloodgateApi#isFloodgatePlayer(UUID)}, never a UUID-prefix
 * test: a Bedrock player who has linked a Java account no longer carries an all-zero-prefix UUID,
 * so the prefix test would misclassify them. {@code isFloodgatePlayer} is Floodgate's own source
 * of truth for "is this UUID currently a connected Bedrock player," which is also why callers must
 * never assume a player's protocol/platform from anything else (ViaVersion may be bridging their
 * protocol version, which says nothing about which client they're on).
 */
public final class FloodgateBridge {

    private final FloodgateApi api;

    private FloodgateBridge(FloodgateApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    /**
     * Guarded factory: a real bridge only when the {@code floodgate} plugin is enabled and its API
     * resolves cleanly; {@link Optional#empty()} otherwise, in which case every player must be
     * served the Java chest-menu path.
     */
    public static Optional<FloodgateBridge> createIfAvailable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            return Optional.empty();
        }
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            if (api == null) {
                return Optional.empty();
            }
            return Optional.of(new FloodgateBridge(api));
        } catch (Throwable t) {
            // Any failure resolving the Floodgate API — an incompatible version, the API not yet
            // initialized, or anything else — must fall back to Java-only rendering rather than
            // breaking plugin startup or leaking a broken bridge to callers.
            return Optional.empty();
        }
    }

    /** Whether {@code player} is connected through Floodgate (a Bedrock client). */
    public boolean isBedrock(UUID player) {
        Objects.requireNonNull(player, "player");
        return api.isFloodgatePlayer(player);
    }

    /**
     * Sends {@code player} a Cumulus {@code SimpleForm} with {@code title}, {@code content}, and
     * one button per entry in {@code buttons} (in order), routing the response back through pure
     * JDK callbacks so no caller ever needs to see a Cumulus type.
     *
     * <p>Expressed entirely in {@link Player}/{@link String}/{@link List}/{@link IntConsumer}/
     * {@link Runnable} terms so that {@link org.xpfarm.dailyq.ui.DailyUi} — which must never
     * import {@code org.geysermc.*} — can drive a Bedrock menu through this one method. {@code
     * onSelect} is invoked with the clicked button's index into {@code buttons}; {@code onClose}
     * is invoked if the player dismisses the form without choosing a button (Floodgate also fires
     * this from a disconnect while the form is open).
     *
     * <p>{@link #isBedrock} is re-checked immediately before {@code sendForm}, exactly like the
     * Pizza plugin's equivalent bridge: {@code FloodgateApi#sendForm} silently returns {@code
     * true} for a Java player's UUID, so an unchecked send would look successful while doing
     * nothing and the caller would never learn a fallback is needed.
     *
     * @return {@code true} if the form was actually sent to {@code player}; {@code false} if this
     *     player cannot be shown one (not a Bedrock player), in which case the caller must fall
     *     back to the Java chest-menu path
     */
    public boolean openForm(
            Player player, String title, String content, List<String> buttons, IntConsumer onSelect,
            Runnable onClose) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(buttons, "buttons");
        Objects.requireNonNull(onSelect, "onSelect");
        Objects.requireNonNull(onClose, "onClose");

        UUID id = player.getUniqueId();
        // Checked immediately before sendForm, per class javadoc: FloodgateApi#sendForm silently
        // returns true for a Java player's UUID, so an unchecked send would look successful while
        // doing nothing.
        if (!isBedrock(id)) {
            return false;
        }

        SimpleForm.Builder builder = SimpleForm.builder().title(title).content(content);
        for (String button : buttons) {
            builder.button(button);
        }
        builder.validResultHandler(response -> onValid(buttons, onSelect, response));
        builder.closedResultHandler(onClose);

        return api.sendForm(id, builder.build());
    }

    private static void onValid(List<String> buttons, IntConsumer onSelect, SimpleFormResponse response) {
        int clicked = response.clickedButtonId();
        if (clicked < 0 || clicked >= buttons.size()) {
            return;
        }
        onSelect.accept(clicked);
    }
}
