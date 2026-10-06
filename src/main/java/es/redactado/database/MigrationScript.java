package es.redactado.database;

/**
 * An extra SQL file, registered with {@link es.redactado.feature.BotFeature#migration}.
 *
 * @param dialect {@code sqlite}, {@code h2} or {@code mariadb}
 * @param version number from the file name, unique for that dialect
 * @param resource classpath path beginning with {@code /}
 */
public record MigrationScript(String dialect, int version, String resource) {}
