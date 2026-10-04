package es.redactado.service;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Everything the menu system reads from the environment, parsed once at startup.
 *
 * <p>The template loads settings through {@link Dotenv}, so this follows that rather than
 * inventing a second mechanism. Every value has a default, so a bot with nothing configured
 * runs on the defaults, and every failure names the setting that is wrong, because a
 * mistyped number in a {@code .env} file is otherwise invisible until a menu misbehaves
 * hours later.
 *
 * <p>Immutable and built once: nothing re-reads the environment, so a running bot cannot
 * change its own session bounds underneath a live message.
 */
public record MenuSettings(
        Path presetsDirectory,
        long sessionMaxSize,
        Duration sessionIdleTtl,
        boolean userPresetsEnabled,
        String defaultPreset,
        Duration cleanUpInterval) {

    /** Where custom preset files are read from. */
    public static final String PRESETS_DIRECTORY = "MENU_PRESETS_DIR";

    /** How many menu messages are remembered at once. */
    public static final String SESSION_MAX_SIZE = "MENU_SESSION_MAX_SIZE";

    /** How long an untouched menu message keeps its history. */
    public static final String SESSION_IDLE_TTL = "MENU_SESSION_IDLE_TTL";

    /** Whether a user's own preset may override their guild's. */
    public static final String USER_PRESETS_ENABLED = "MENU_USER_PRESETS_ENABLED";

    /** Which preset is used when nothing else says otherwise. */
    public static final String DEFAULT_PRESET = "MENU_DEFAULT_PRESET";

    /**
     * How often pending session eviction is drained.
     *
     * <p>Not a setting: it is an implementation detail of this service, and a test needs to
     * shorten it to prove the schedule is cancelled on shutdown.
     */
    static final String CLEAN_UP_INTERVAL = "1 minute";

    public MenuSettings {
        if (presetsDirectory == null) {
            throw new IllegalArgumentException(PRESETS_DIRECTORY + " must not be null");
        }
        if (sessionMaxSize < 1) {
            throw new IllegalArgumentException(
                    SESSION_MAX_SIZE + " must be at least 1, got " + sessionMaxSize);
        }
        if (sessionIdleTtl == null || sessionIdleTtl.isNegative() || sessionIdleTtl.isZero()) {
            throw new IllegalArgumentException(
                    SESSION_IDLE_TTL + " must be a positive duration, got " + sessionIdleTtl);
        }
        if (cleanUpInterval == null || cleanUpInterval.isNegative() || cleanUpInterval.isZero()) {
            throw new IllegalArgumentException("The clean-up interval must be positive");
        }
        if (defaultPreset == null || defaultPreset.isBlank()) {
            throw new IllegalArgumentException(DEFAULT_PRESET + " must not be blank");
        }
    }

    /**
     * Reads the settings from the environment, using the documented defaults.
     *
     * @param dotenv where the template's settings come from
     * @return the parsed settings
     * @throws IllegalArgumentException if a value is present but not usable, naming the
     *     setting so the operator knows which line of the {@code .env} file to fix
     */
    public static MenuSettings from(Dotenv dotenv) {
        return new MenuSettings(
                Path.of(dotenv.get(PRESETS_DIRECTORY, "presets")),
                positiveLong(dotenv, SESSION_MAX_SIZE, 50_000L),
                duration(dotenv, SESSION_IDLE_TTL, Duration.ofMinutes(30)),
                bool(dotenv, USER_PRESETS_ENABLED, false),
                dotenv.get(DEFAULT_PRESET, "default"),
                Duration.ofMinutes(1));
    }

    /** The same settings with a different clean-up interval, which only a test needs. */
    MenuSettings withCleanUpInterval(Duration interval) {
        return new MenuSettings(
                presetsDirectory,
                sessionMaxSize,
                sessionIdleTtl,
                userPresetsEnabled,
                defaultPreset,
                interval);
    }

    private static long positiveLong(Dotenv dotenv, String key, long fallback) {
        String raw = dotenv.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        long parsed;
        try {
            parsed = Long.parseLong(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a whole number, got '" + raw + "'");
        }
        if (parsed < 1) {
            throw new IllegalArgumentException(key + " must be at least 1, got " + parsed);
        }
        return parsed;
    }

    private static Duration duration(Dotenv dotenv, String key, Duration fallback) {
        String raw = dotenv.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String text = raw.strip().toLowerCase(java.util.Locale.ROOT);
        try {
            if (text.endsWith("ms")) {
                return Duration.ofMillis(
                        Long.parseLong(text.substring(0, text.length() - 2).strip()));
            }
            if (text.endsWith("s")) {
                return Duration.ofSeconds(
                        Long.parseLong(text.substring(0, text.length() - 1).strip()));
            }
            if (text.endsWith("m")) {
                return Duration.ofMinutes(
                        Long.parseLong(text.substring(0, text.length() - 1).strip()));
            }
            if (text.endsWith("h")) {
                return Duration.ofHours(
                        Long.parseLong(text.substring(0, text.length() - 1).strip()));
            }
            return Duration.ofMinutes(Long.parseLong(text));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    key + " must be a duration such as 30m, 90s or 2h, got '" + raw + "'");
        }
    }

    private static boolean bool(Dotenv dotenv, String key, boolean fallback) {
        String raw = dotenv.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String text = raw.strip().toLowerCase(java.util.Locale.ROOT);
        return switch (text) {
            case "true", "yes", "1", "on" -> true;
            case "false", "no", "0", "off" -> false;
            default ->
                    throw new IllegalArgumentException(
                            key + " must be true or false, got '" + raw + "'");
        };
    }
}
