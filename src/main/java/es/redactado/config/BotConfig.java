package es.redactado.config;

import es.redactado.service.MenuSettings;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.requests.GatewayIntent;

/**
 * Settings the running bot actually uses, after the YAML file and the environment have been
 * merged and checked.
 *
 * <p>Built once at startup. A SQLite database is given a pool of one here, so later code can
 * trust {@link #pool()} instead of remembering the rule.
 */
public record BotConfig(
        String token,
        List<GatewayIntent> intents,
        String status,
        boolean autoReconnect,
        int shards,
        DatabaseType databaseType,
        String dbHost,
        int dbPort,
        String dbName,
        String dbUser,
        String dbPassword,
        Path dbPath,
        PoolSettings pool,
        HibernateSettings hibernate,
        MenuSettings menu,
        CommandScope commandScope,
        long commandGuildId,
        Duration defaultCooldown,
        boolean sentryEnabled,
        String sentryDsn) {

    public enum DatabaseType {
        SQLITE,
        MARIADB,
        H2
    }

    public enum CommandScope {
        GUILD,
        GLOBAL
    }

    public enum SchemaMode {
        VALIDATE,
        UPDATE
    }

    public record PoolSettings(
            int minIdle,
            int maxSize,
            long idleTimeoutMillis,
            long maxLifetimeMillis,
            long connectionTimeoutMillis,
            long leakDetectionMillis) {}

    public record HibernateSettings(
            boolean showSql,
            boolean formatSql,
            boolean highlightSql,
            int batchSize,
            SchemaMode schema) {}

    public static BotConfig defaults() {
        return from(new ConfigFile());
    }

    public static BotConfig from(ConfigFile file) {
        DatabaseType type = enumValue(DatabaseType.class, file.database().type(), "database.type");
        int maxPool = file.pool().maxSize();
        int minIdle = file.pool().minIdle();
        if (type == DatabaseType.SQLITE) {
            maxPool = 1;
            minIdle = 1;
        }
        if (minIdle < 1 || maxPool < minIdle) {
            throw new IllegalArgumentException(
                    "pool.minIdle must be at least 1 and pool.maxSize must be at least minIdle, got"
                            + " "
                            + file.pool().minIdle()
                            + " and "
                            + file.pool().maxSize());
        }
        if (file.bot().shards() < 1) {
            throw new IllegalArgumentException(
                    "bot.shards must be at least 1, got " + file.bot().shards());
        }
        if (file.pool().connectionTimeoutMillis() < 1 || file.hibernate().batchSize() < 1) {
            throw new IllegalArgumentException(
                    "pool timeouts and hibernate.batchSize must be at least 1");
        }
        if (file.database().port() < 1) {
            throw new IllegalArgumentException("database.port must be at least 1");
        }
        if (file.database().name() == null || file.database().name().isBlank()) {
            throw new IllegalArgumentException("database.name must not be blank");
        }
        List<GatewayIntent> intents = intents(file.bot().intents());
        SchemaMode schema =
                enumValue(SchemaMode.class, file.hibernate().schema(), "hibernate.schema");
        CommandScope scope =
                enumValue(CommandScope.class, file.commands().scope(), "commands.scope");
        long sessionMax = file.menu().sessionMaxSize();
        if (sessionMax < 1) {
            throw new IllegalArgumentException(
                    "menu.sessionMaxSize must be at least 1, got " + sessionMax);
        }
        if (file.menu().maxInFlight() < 0) {
            throw new IllegalArgumentException(
                    "menu.maxInFlight must be 0 or more, got " + file.menu().maxInFlight());
        }
        String preset = file.menu().defaultPreset();
        if (preset == null || preset.isBlank()) {
            throw new IllegalArgumentException("menu.defaultPreset must not be blank");
        }
        String path = file.database().path() == null ? "./database" : file.database().path();
        return new BotConfig(
                file.bot().token() == null ? "" : file.bot().token(),
                intents,
                file.bot().status() == null ? "" : file.bot().status(),
                file.bot().autoReconnect(),
                file.bot().shards(),
                type,
                file.database().host(),
                file.database().port(),
                file.database().name(),
                file.database().user() == null ? "" : file.database().user(),
                file.database().password() == null ? "" : file.database().password(),
                Path.of(path),
                new PoolSettings(
                        minIdle,
                        maxPool,
                        file.pool().idleTimeoutMillis(),
                        file.pool().maxLifetimeMillis(),
                        file.pool().connectionTimeoutMillis(),
                        file.pool().leakDetectionMillis()),
                new HibernateSettings(
                        file.hibernate().showSql(),
                        file.hibernate().formatSql(),
                        file.hibernate().highlightSql(),
                        file.hibernate().batchSize(),
                        schema),
                new MenuSettings(
                        Path.of(file.menu().presetsDirectory()),
                        sessionMax,
                        Durations.positive(file.menu().sessionIdleTtl(), "menu.sessionIdleTtl"),
                        file.menu().userPresetsEnabled(),
                        preset.strip(),
                        Duration.ofMinutes(1),
                        file.menu().maxInFlight()),
                scope,
                file.commands().guildId(),
                Durations.zeroOrPositive(
                        file.commands().defaultCooldown(), "commands.defaultCooldown"),
                file.sentry().enabled(),
                file.sentry().dsn() == null ? "" : file.sentry().dsn());
    }

    /** A token that was never replaced. Startup refuses to connect with it. */
    public boolean tokenIsPlaceholder() {
        return token == null || token.isBlank() || token.equals("change-me");
    }

    private static List<GatewayIntent> intents(List<String> names) {
        if (names == null || names.isEmpty()) {
            throw new IllegalArgumentException("bot.intents must name at least one intent");
        }
        List<GatewayIntent> intents = new ArrayList<>();
        for (String name : names) {
            try {
                intents.add(GatewayIntent.valueOf(name.strip().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("bot.intents has unknown intent '" + name + "'");
            }
        }
        return List.copyOf(intents);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String name) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        try {
            return Enum.valueOf(type, raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + " has unknown value '" + raw + "'");
        }
    }
}
