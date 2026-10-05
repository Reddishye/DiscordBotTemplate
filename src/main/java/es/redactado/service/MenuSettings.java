package es.redactado.service;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Menu bounds, read once from {@code config.yml} and held for the life of the process.
 *
 * <p>Immutable on purpose: a running bot does not change how many sessions it keeps underneath
 * a live message. The clean-up interval is not a setting. It is how often this process drains
 * expired sessions, and tests shorten it.
 */
public record MenuSettings(
        Path presetsDirectory,
        long sessionMaxSize,
        Duration sessionIdleTtl,
        boolean userPresetsEnabled,
        String defaultPreset,
        Duration cleanUpInterval,
        int maxInFlight) {

    public MenuSettings {
        if (presetsDirectory == null) {
            throw new IllegalArgumentException("menu.presetsDirectory must not be null");
        }
        if (sessionMaxSize < 1) {
            throw new IllegalArgumentException(
                    "menu.sessionMaxSize must be at least 1, got " + sessionMaxSize);
        }
        if (sessionIdleTtl == null || sessionIdleTtl.isNegative() || sessionIdleTtl.isZero()) {
            throw new IllegalArgumentException(
                    "menu.sessionIdleTtl must be a positive duration, got " + sessionIdleTtl);
        }
        if (cleanUpInterval == null || cleanUpInterval.isNegative() || cleanUpInterval.isZero()) {
            throw new IllegalArgumentException("The clean-up interval must be positive");
        }
        if (defaultPreset == null || defaultPreset.isBlank()) {
            throw new IllegalArgumentException("menu.defaultPreset must not be blank");
        }
        if (maxInFlight < 0) {
            throw new IllegalArgumentException(
                    "menu.maxInFlight must be 0 or more, got " + maxInFlight);
        }
    }

    public static MenuSettings defaults() {
        return new MenuSettings(
                Path.of("presets"),
                50_000L,
                Duration.ofMinutes(30),
                false,
                "default",
                Duration.ofMinutes(1),
                0);
    }

    /** The same settings with a different clean-up interval, which only a test needs. */
    MenuSettings withCleanUpInterval(Duration interval) {
        return new MenuSettings(
                presetsDirectory,
                sessionMaxSize,
                sessionIdleTtl,
                userPresetsEnabled,
                defaultPreset,
                interval,
                maxInFlight);
    }
}
