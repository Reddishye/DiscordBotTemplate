package es.redactado.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Applies environment variables on top of a loaded {@link ConfigFile}.
 *
 * <p>A set variable wins. A missing or blank variable leaves the file value alone, so an empty
 * {@code BOT_TOKEN=} in a compose file does not wipe a token that was written into the YAML.
 * The result is a new record. Nothing here writes the file, which is what keeps a container
 * override from being saved back over the operator's copy.
 *
 * <p>Names follow the path: {@code BOT_DATABASE_HOST}. The older {@code DISCORD_TOKEN} and
 * {@code DB_*} names still work when the {@code BOT_*} name is unset, so an existing environment
 * keeps booting.
 */
public final class EnvOverlay {

    private EnvOverlay() {}

    public static ConfigFile apply(ConfigFile file, Map<String, String> env) {
        return new ConfigFile(
                bot(file.bot(), env),
                database(file.database(), env),
                pool(file.pool(), env),
                hibernate(file.hibernate(), env),
                menu(file.menu(), env),
                commands(file.commands(), env),
                sentry(file.sentry(), env));
    }

    private static ConfigFile.BotFile bot(ConfigFile.BotFile file, Map<String, String> env) {
        String intents = first(env, "BOT_INTENTS");
        return new ConfigFile.BotFile(
                over(env, file.token(), "BOT_TOKEN", "DISCORD_TOKEN"),
                intents == null ? file.intents() : split(intents),
                over(env, file.status(), "BOT_STATUS"),
                bool(env, file.autoReconnect(), "BOT_AUTO_RECONNECT"),
                integer(env, file.shards(), "BOT_SHARDS"));
    }

    private static ConfigFile.DatabaseFile database(
            ConfigFile.DatabaseFile file, Map<String, String> env) {
        return new ConfigFile.DatabaseFile(
                over(env, file.type(), "BOT_DATABASE_TYPE", "DB_TYPE"),
                over(env, file.host(), "BOT_DATABASE_HOST", "DB_HOST"),
                integer(env, file.port(), "BOT_DATABASE_PORT", "DB_PORT"),
                over(env, file.name(), "BOT_DATABASE_NAME", "DB_NAME"),
                over(env, file.user(), "BOT_DATABASE_USER", "DB_USER"),
                over(env, file.password(), "BOT_DATABASE_PASSWORD", "DB_PASSWORD"),
                over(env, file.path(), "BOT_DATABASE_PATH", "DB_PATH"));
    }

    private static ConfigFile.PoolFile pool(ConfigFile.PoolFile file, Map<String, String> env) {
        return new ConfigFile.PoolFile(
                integer(env, file.minIdle(), "BOT_POOL_MIN_IDLE", "HIKARI_MIN_IDLE"),
                integer(env, file.maxSize(), "BOT_POOL_MAX", "HIKARI_MAX_POOL_SIZE"),
                number(
                        env,
                        file.idleTimeoutMillis(),
                        "BOT_POOL_IDLE_TIMEOUT",
                        "HIKARI_IDLE_TIMEOUT"),
                number(
                        env,
                        file.maxLifetimeMillis(),
                        "BOT_POOL_MAX_LIFETIME",
                        "HIKARI_MAX_LIFETIME"),
                number(
                        env,
                        file.connectionTimeoutMillis(),
                        "BOT_POOL_CONNECTION_TIMEOUT",
                        "HIKARI_CONNECTION_TIMEOUT"),
                number(
                        env,
                        file.leakDetectionMillis(),
                        "BOT_POOL_LEAK_DETECTION",
                        "HIKARI_LEAK_DETECTION"));
    }

    private static ConfigFile.HibernateFile hibernate(
            ConfigFile.HibernateFile file, Map<String, String> env) {
        return new ConfigFile.HibernateFile(
                bool(env, file.showSql(), "BOT_HIBERNATE_SHOW_SQL", "HIBERNATE_SHOW_SQL"),
                bool(env, file.formatSql(), "BOT_HIBERNATE_FORMAT_SQL", "HIBERNATE_FORMAT_SQL"),
                bool(
                        env,
                        file.highlightSql(),
                        "BOT_HIBERNATE_HIGHLIGHT_SQL",
                        "HIBERNATE_HIGHLIGHT_SQL"),
                integer(env, file.batchSize(), "BOT_HIBERNATE_BATCH_SIZE"),
                over(env, file.schema(), "BOT_HIBERNATE_SCHEMA"));
    }

    private static ConfigFile.MenuFile menu(ConfigFile.MenuFile file, Map<String, String> env) {
        return new ConfigFile.MenuFile(
                over(env, file.presetsDirectory(), "BOT_MENU_PRESETS_DIR", "MENU_PRESETS_DIR"),
                number(
                        env,
                        file.sessionMaxSize(),
                        "BOT_MENU_SESSION_MAX_SIZE",
                        "MENU_SESSION_MAX_SIZE"),
                over(
                        env,
                        file.sessionIdleTtl(),
                        "BOT_MENU_SESSION_IDLE_TTL",
                        "MENU_SESSION_IDLE_TTL"),
                bool(
                        env,
                        file.userPresetsEnabled(),
                        "BOT_MENU_USER_PRESETS_ENABLED",
                        "MENU_USER_PRESETS_ENABLED"),
                over(env, file.defaultPreset(), "BOT_MENU_DEFAULT_PRESET", "MENU_DEFAULT_PRESET"),
                integer(env, file.maxInFlight(), "BOT_MENU_MAX_IN_FLIGHT"));
    }

    private static ConfigFile.CommandsFile commands(
            ConfigFile.CommandsFile file, Map<String, String> env) {
        return new ConfigFile.CommandsFile(
                over(env, file.scope(), "BOT_COMMANDS_SCOPE"),
                number(env, file.guildId(), "BOT_COMMANDS_GUILD_ID"),
                over(env, file.defaultCooldown(), "BOT_COMMANDS_DEFAULT_COOLDOWN"));
    }

    private static ConfigFile.SentryFile sentry(
            ConfigFile.SentryFile file, Map<String, String> env) {
        return new ConfigFile.SentryFile(
                bool(env, file.enabled(), "BOT_SENTRY_ENABLED"),
                over(env, file.dsn(), "BOT_SENTRY_DSN", "SENTRY_DSN"));
    }

    private static List<String> split(String raw) {
        List<String> names = new ArrayList<>();
        for (String part : raw.split(",")) {
            String name = part.strip();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private static String over(Map<String, String> env, String fallback, String... keys) {
        String chosen = first(env, keys);
        return chosen == null ? fallback : chosen;
    }

    private static String first(Map<String, String> env, String... keys) {
        for (String key : keys) {
            String raw = env.get(key);
            if (raw != null && !raw.isBlank()) {
                return raw.strip();
            }
        }
        return null;
    }

    private static boolean bool(Map<String, String> env, boolean fallback, String... keys) {
        String raw = first(env, keys);
        if (raw == null) {
            return fallback;
        }
        String name = keys[0];
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1", "on" -> true;
            case "false", "no", "0", "off" -> false;
            default ->
                    throw new IllegalArgumentException(
                            name + " must be true or false, got '" + raw + "'");
        };
    }

    private static int integer(Map<String, String> env, int fallback, String... keys) {
        return (int) number(env, fallback, keys);
    }

    private static long number(Map<String, String> env, long fallback, String... keys) {
        String raw = first(env, keys);
        if (raw == null) {
            return fallback;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    keys[0] + " must be a whole number, got '" + raw + "'");
        }
    }
}
