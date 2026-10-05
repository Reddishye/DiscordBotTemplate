package es.redactado.config;

import java.time.Duration;
import java.util.Locale;

/** Parses the duration text used in config.yml and in environment overrides. */
public final class Durations {

    private Durations() {}

    /**
     * @param raw the text, such as {@code 30m}, {@code 90s}, {@code 2h}, {@code 500ms}, or a bare
     *     number of minutes
     * @param name the setting name included in the failure
     * @return the duration
     * @throws IllegalArgumentException if the text is not a positive duration
     */
    public static Duration positive(String raw, String name) {
        Duration parsed = parse(raw, name);
        if (parsed.isZero() || parsed.isNegative()) {
            throw new IllegalArgumentException(
                    name + " must be a positive duration, got '" + raw + "'");
        }
        return parsed;
    }

    /**
     * Like {@link #positive}, and also accepts zero, which means "off".
     *
     * @param raw the text
     * @param name the setting name included in the failure
     * @return the duration, which may be zero
     */
    public static Duration zeroOrPositive(String raw, String name) {
        Duration parsed = parse(raw, name);
        if (parsed.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative, got '" + raw + "'");
        }
        return parsed;
    }

    private static Duration parse(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(name + " must be a duration such as 30m, 90s or 2h");
        }
        String text = raw.strip().toLowerCase(Locale.ROOT);
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
                    name + " must be a duration such as 30m, 90s or 2h, got '" + raw + "'");
        }
    }
}
