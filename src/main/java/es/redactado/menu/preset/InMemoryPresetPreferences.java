package es.redactado.menu.preset;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link PresetPreferences} that keeps everything in memory.
 *
 * <p>Suitable for a bot that does not want a preferences table, and for tests. Every
 * lookup is already resolved, so the futures are returned completed and
 * {@code PresetResolver} never has to wait for one.
 *
 * <p>Nothing is persisted: a restart loses every stored preference.
 */
public final class InMemoryPresetPreferences implements PresetPreferences {

    private final Map<Long, String> byGuild = new ConcurrentHashMap<>();
    private final Map<Long, String> byUser = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Optional<String>> guildPreset(long guildId) {
        return CompletableFuture.completedFuture(Optional.ofNullable(byGuild.get(guildId)));
    }

    @Override
    public CompletableFuture<Optional<String>> userPreset(long userId) {
        return CompletableFuture.completedFuture(Optional.ofNullable(byUser.get(userId)));
    }

    /**
     * Stores the preset a guild asked for, replacing any previous choice.
     *
     * @param guildId the guild id
     * @param name the preset name
     * @throws NullPointerException if {@code name} is null
     */
    public void setGuild(long guildId, String name) {
        byGuild.put(guildId, Objects.requireNonNull(name, "name"));
    }

    /**
     * Forgets the guild's choice, so the next level of resolution applies.
     *
     * @param guildId the guild id
     */
    public void clearGuild(long guildId) {
        byGuild.remove(guildId);
    }

    /**
     * Stores the preset a user asked for, replacing any previous choice.
     *
     * @param userId the user id
     * @param name the preset name
     * @throws NullPointerException if {@code name} is null
     */
    public void setUser(long userId, String name) {
        byUser.put(userId, Objects.requireNonNull(name, "name"));
    }

    /**
     * Forgets the user's choice, so the next level of resolution applies.
     *
     * @param userId the user id
     */
    public void clearUser(long userId) {
        byUser.remove(userId);
    }
}
