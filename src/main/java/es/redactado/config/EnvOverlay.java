package es.redactado.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Replaces {@link ConfigFile} values with environment variables.
 *
 * <p>The variable name is the YAML path, in capitals, with dots as underscores and a {@code BOT_}
 * prefix. {@code database.host} is {@code BOT_DATABASE_HOST}. {@code bot.token} is {@code
 * BOT_TOKEN}, because the path already begins with {@code bot}. A missing or blank variable
 * leaves the file value. The file is not written.
 *
 * <p>Older names ({@code DISCORD_TOKEN}, {@code DB_HOST}, {@code MENU_*} and the short pool
 * names) still fill the same fields when the {@code BOT_} name above is unset.
 */
public final class EnvOverlay {

    private static final Map<String, List<String>> OLD_NAMES =
            Map.ofEntries(
                    Map.entry("BOT_TOKEN", List.of("DISCORD_TOKEN")),
                    Map.entry("BOT_DATABASE_TYPE", List.of("DB_TYPE")),
                    Map.entry("BOT_DATABASE_HOST", List.of("DB_HOST")),
                    Map.entry("BOT_DATABASE_PORT", List.of("DB_PORT")),
                    Map.entry("BOT_DATABASE_NAME", List.of("DB_NAME")),
                    Map.entry("BOT_DATABASE_USER", List.of("DB_USER")),
                    Map.entry("BOT_DATABASE_PASSWORD", List.of("DB_PASSWORD")),
                    Map.entry("BOT_DATABASE_PATH", List.of("DB_PATH")),
                    Map.entry("BOT_POOL_MIN_IDLE", List.of("HIKARI_MIN_IDLE")),
                    Map.entry("BOT_POOL_MAX_SIZE", List.of("BOT_POOL_MAX", "HIKARI_MAX_POOL_SIZE")),
                    Map.entry(
                            "BOT_POOL_IDLE_TIMEOUT_MILLIS",
                            List.of("BOT_POOL_IDLE_TIMEOUT", "HIKARI_IDLE_TIMEOUT")),
                    Map.entry(
                            "BOT_POOL_MAX_LIFETIME_MILLIS",
                            List.of("BOT_POOL_MAX_LIFETIME", "HIKARI_MAX_LIFETIME")),
                    Map.entry(
                            "BOT_POOL_CONNECTION_TIMEOUT_MILLIS",
                            List.of("BOT_POOL_CONNECTION_TIMEOUT", "HIKARI_CONNECTION_TIMEOUT")),
                    Map.entry(
                            "BOT_POOL_LEAK_DETECTION_MILLIS",
                            List.of("BOT_POOL_LEAK_DETECTION", "HIKARI_LEAK_DETECTION")),
                    Map.entry("BOT_HIBERNATE_SHOW_SQL", List.of("HIBERNATE_SHOW_SQL")),
                    Map.entry("BOT_HIBERNATE_FORMAT_SQL", List.of("HIBERNATE_FORMAT_SQL")),
                    Map.entry("BOT_HIBERNATE_HIGHLIGHT_SQL", List.of("HIBERNATE_HIGHLIGHT_SQL")),
                    Map.entry(
                            "BOT_MENU_PRESETS_DIRECTORY",
                            List.of("BOT_MENU_PRESETS_DIR", "MENU_PRESETS_DIR")),
                    Map.entry("BOT_MENU_SESSION_MAX_SIZE", List.of("MENU_SESSION_MAX_SIZE")),
                    Map.entry("BOT_MENU_SESSION_IDLE_TTL", List.of("MENU_SESSION_IDLE_TTL")),
                    Map.entry(
                            "BOT_MENU_USER_PRESETS_ENABLED", List.of("MENU_USER_PRESETS_ENABLED")),
                    Map.entry("BOT_MENU_DEFAULT_PRESET", List.of("MENU_DEFAULT_PRESET")),
                    Map.entry("BOT_SENTRY_DSN", List.of("SENTRY_DSN")));

    private EnvOverlay() {}

    public static ConfigFile apply(ConfigFile file, Map<String, String> env) {
        return (ConfigFile) overlay(file, ConfigFile.class, "", env);
    }

    private static Object overlay(
            Object value, Class<?> type, String path, Map<String, String> env) {
        if (type.isRecord()) {
            return overlayRecord(value, type, path, env);
        }
        String raw = read(env, envName(path));
        if (raw == null) {
            return value;
        }
        return convert(type, raw, envName(path));
    }

    private static Object overlayRecord(
            Object value, Class<?> type, String path, Map<String, String> env) {
        if (value == null) {
            return null;
        }
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] args = new Object[components.length];
        try {
            for (int i = 0; i < components.length; i++) {
                RecordComponent component = components[i];
                types[i] = component.getType();
                String child =
                        path.isEmpty() ? component.getName() : path + "." + component.getName();
                Object current = component.getAccessor().invoke(value);
                args[i] = overlay(current, component.getType(), child, env);
            }
            Constructor<?> constructor = type.getDeclaredConstructor(types);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Could not apply environment to " + type.getSimpleName(), e);
        }
    }

    /** {@code database.host} becomes {@code BOT_DATABASE_HOST}. {@code bot.token} becomes {@code BOT_TOKEN}. */
    static String envName(String path) {
        String snake = snakePath(path);
        if (snake.startsWith("BOT_")) {
            return snake;
        }
        return "BOT_" + snake;
    }

    private static String snakePath(String path) {
        String[] parts = path.split("\\.");
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (!joined.isEmpty()) {
                joined.append('_');
            }
            joined.append(snake(part));
        }
        return joined.toString();
    }

    private static String snake(String camel) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                out.append('_');
            }
            out.append(Character.toUpperCase(c));
        }
        return out.toString();
    }

    private static String read(Map<String, String> env, String key) {
        String direct = present(env.get(key));
        if (direct != null) {
            return direct;
        }
        for (String older : OLD_NAMES.getOrDefault(key, List.of())) {
            String value = present(env.get(older));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String present(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.strip();
    }

    private static Object convert(Class<?> type, String raw, String key) {
        if (type == String.class) {
            return raw;
        }
        if (type == boolean.class || type == Boolean.class) {
            return bool(raw, key);
        }
        if (type == int.class || type == Integer.class) {
            return (int) number(raw, key);
        }
        if (type == long.class || type == Long.class) {
            return number(raw, key);
        }
        if (List.class.isAssignableFrom(type)) {
            return split(raw);
        }
        throw new IllegalArgumentException(key + " cannot override " + type.getSimpleName());
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

    private static boolean bool(String raw, String key) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1", "on" -> true;
            case "false", "no", "0", "off" -> false;
            default ->
                    throw new IllegalArgumentException(
                            key + " must be true or false, got '" + raw + "'");
        };
    }

    private static long number(String raw, String key) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a whole number, got '" + raw + "'");
        }
    }
}
