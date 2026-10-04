package es.redactado.menu.preset;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Where a guild or a user has asked for a particular preset.
 *
 * <p>Lookups return a future rather than a value because the real answer usually
 * lives in a database. Returning {@link Optional#empty()} means "no preference
 * stored", which is different from "the lookup failed": an implementation that
 * cannot reach its database should complete the future exceptionally and let
 * {@code PresetResolver} fall back, rather than reporting that no preference
 * exists and overwriting a stored one.
 *
 * <p><strong>Implementations must not block.</strong> These are called while
 * resolving a menu for an interaction, so a blocking implementation stalls the
 * interaction thread. An implementation backed by a database should query
 * asynchronously and cache, since a preset is looked up on every render and
 * changes rarely.
 */
public interface PresetPreferences {

    /**
     * The preset a guild asked for.
     *
     * @param guildId the guild id
     * @return a future for the preset name, or empty when none is stored; never
     *     {@code null}
     */
    CompletableFuture<Optional<String>> guildPreset(long guildId);

    /**
     * The preset a user asked for.
     *
     * @param userId the user id
     * @return a future for the preset name, or empty when none is stored; never
     *     {@code null}
     */
    CompletableFuture<Optional<String>> userPreset(long userId);
}
