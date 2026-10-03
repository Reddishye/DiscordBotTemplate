package es.redactado.menu.core;

import es.redactado.menu.api.Menu;
import es.redactado.menu.preset.Preset;
import es.redactado.menu.preset.PresetPreferences;
import es.redactado.menu.preset.PresetRegistry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides which preset one interaction renders with.
 *
 * <p>Four levels, most specific first: what the menu itself forces, what the guild
 * chose, what the user chose, and finally the registry default. Each level is
 * consulted only if the one above it produced nothing, and a level that names a preset
 * nobody has any more is skipped rather than treated as the answer. That is what makes
 * deleting a preset file safe on a running bot.
 *
 * <p><strong>Resolution never fails and never blocks.</strong> It ends at the registry
 * default no matter what happens: a broken preferences store should degrade to the
 * default look, not leave a menu unable to render. A preferences lookup that fails is
 * logged and treated as "no preference", and the user level is not queried at all when a
 * higher level already answered, which keeps an unnecessary query off the common path.
 */
public final class PresetResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(PresetResolver.class);

    private final PresetRegistry registry;
    private final PresetPreferences preferences;
    private final boolean userPresetsEnabled;
    private final Executor executor;

    /**
     * Creates a resolver.
     *
     * @param registry where presets are looked up and where the default comes from
     * @param preferences where per-guild and per-user choices come from
     * @param userPresetsEnabled whether a user's own choice may override their guild's,
     *     which some bots will not want
     */
    public PresetResolver(
            PresetRegistry registry, PresetPreferences preferences, boolean userPresetsEnabled) {
        this(
                registry,
                preferences,
                userPresetsEnabled,
                CompletableFuture.delayedExecutor(0, java.util.concurrent.TimeUnit.NANOSECONDS));
    }

    /**
     * Creates a resolver with an explicit continuation executor.
     *
     * <p>Only for tests and for a bot that wants the rest of the chain on its own pool.
     *
     * @param registry where presets are looked up and where the default comes from
     * @param preferences where per-guild and per-user choices come from
     * @param userPresetsEnabled whether a user's own choice may override their guild's
     * @param executor runs the continuation after each preferences lookup
     */
    public PresetResolver(
            PresetRegistry registry,
            PresetPreferences preferences,
            boolean userPresetsEnabled,
            Executor executor) {
        this.registry = registry;
        this.preferences = preferences;
        this.userPresetsEnabled = userPresetsEnabled;
        this.executor = executor;
    }

    /**
     * Finds the preset for one interaction.
     *
     * @param menu the menu being rendered
     * @param guildId the guild, or {@code 0} in a direct message
     * @param userId the user
     * @return a future that always yields a preset; never fails and never returns null
     */
    public CompletableFuture<Preset> resolve(Menu menu, long guildId, long userId) {
        Optional<Preset> forced = lookup(menu.presetName(), "menu " + menu.id());
        if (forced.isPresent()) {
            return CompletableFuture.completedFuture(forced.get());
        }

        if (guildId == 0L) {
            // A direct message has no guild, and there is no guild preference to read.
            return userLevel(userId);
        }

        return guildLevel(guildId, userId);
    }

    private CompletableFuture<Preset> guildLevel(long guildId, long userId) {
        return safe(preferences.guildPreset(guildId), "guild " + guildId)
                .thenComposeAsync(
                        name -> {
                            Optional<Preset> found = lookup(name, "guild " + guildId);
                            return found.isPresent()
                                    ? CompletableFuture.completedFuture(found.get())
                                    : userLevel(userId);
                        },
                        executor);
    }

    private CompletableFuture<Preset> userLevel(long userId) {
        if (!userPresetsEnabled) {
            return CompletableFuture.completedFuture(registry.defaultPreset());
        }
        return safe(preferences.userPreset(userId), "user " + userId)
                .thenApplyAsync(
                        name -> lookup(name, "user " + userId).orElseGet(registry::defaultPreset),
                        executor);
    }

    /** Resolves a name, skipping it with a debug log when the registry has no such preset. */
    private Optional<Preset> lookup(Optional<String> name, String source) {
        if (name.isEmpty() || name.get().isBlank()) {
            return Optional.empty();
        }
        Optional<Preset> found = registry.find(name.get());
        if (found.isEmpty()) {
            LOGGER.debug("No preset named '{}' for {}, trying the next level", name.get(), source);
        }
        return found;
    }

    /** Turns a failed preferences lookup into an empty one, so resolution cannot fail. */
    private CompletableFuture<Optional<String>> safe(
            CompletableFuture<Optional<String>> lookup, String source) {
        return lookup.handle(
                (name, error) -> {
                    if (error != null) {
                        LOGGER.warn(
                                "Could not read the preset preference for {}, treating it as unset",
                                source,
                                error);
                        return Optional.<String>empty();
                    }
                    return name == null ? Optional.<String>empty() : name;
                });
    }
}
