package es.redactado.service;

import es.redactado.database.DatabaseManager;
import es.redactado.database.model.PreferenceScope;
import es.redactado.database.model.PresetPreference;
import es.redactado.menu.preset.PresetPreferences;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@link PresetPreferences} backed by the database.
 *
 * <p>Lookups return a future and do the query on the I/O executor, which is the contract the
 * menu resolver requires: the call itself must not block.
 */
public final class StoredPresetPreferences implements PresetPreferences {

    private final DatabaseManager database;

    public StoredPresetPreferences(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public CompletableFuture<Optional<String>> guildPreset(long guildId) {
        return find(PreferenceScope.GUILD, guildId);
    }

    @Override
    public CompletableFuture<Optional<String>> userPreset(long userId) {
        return find(PreferenceScope.USER, userId);
    }

    public CompletableFuture<Void> setGuild(long guildId, String name) {
        return store(PreferenceScope.GUILD, guildId, name);
    }

    public CompletableFuture<Void> setUser(long userId, String name) {
        return store(PreferenceScope.USER, userId, name);
    }

    public CompletableFuture<Void> clearGuild(long guildId) {
        return clear(PreferenceScope.GUILD, guildId);
    }

    public CompletableFuture<Void> clearUser(long userId) {
        return clear(PreferenceScope.USER, userId);
    }

    private CompletableFuture<Optional<String>> find(PreferenceScope scope, long subjectId) {
        return database.readAsync(
                session ->
                        session.createQuery(
                                        "from PresetPreference p where p.scope = :scope and"
                                                + " p.subjectId = :id",
                                        PresetPreference.class)
                                .setParameter("scope", scope)
                                .setParameter("id", subjectId)
                                .uniqueResultOptional()
                                .map(PresetPreference::getPresetName));
    }

    private CompletableFuture<Void> store(PreferenceScope scope, long subjectId, String name) {
        Objects.requireNonNull(name, "name");
        return database.inTransactionAsync(
                session -> {
                    PresetPreference row =
                            session.createQuery(
                                            "from PresetPreference p where p.scope = :scope and"
                                                    + " p.subjectId = :id",
                                            PresetPreference.class)
                                    .setParameter("scope", scope)
                                    .setParameter("id", subjectId)
                                    .uniqueResult();
                    if (row == null) {
                        session.persist(new PresetPreference(scope, subjectId, name));
                    } else {
                        row.setPresetName(name);
                    }
                    return null;
                });
    }

    private CompletableFuture<Void> clear(PreferenceScope scope, long subjectId) {
        return database.inTransactionAsync(
                session -> {
                    session.createMutationQuery(
                                    "delete from PresetPreference p where p.scope = :scope and"
                                            + " p.subjectId = :id")
                            .setParameter("scope", scope)
                            .setParameter("id", subjectId)
                            .executeUpdate();
                    return null;
                });
    }
}
