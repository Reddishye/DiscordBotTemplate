package es.redactado.database;

/**
 * One SQL script a feature wants applied for a single database dialect.
 *
 * <p>The template's own scripts are version 1 and live in {@code db/migration}. A feature starts
 * at 2. The same version may exist for {@code sqlite} and {@code mariadb}; it may not exist
 * twice for one of them. Startup throws rather than picking an order.
 *
 * @param dialect {@code sqlite}, {@code mariadb} or {@code h2}
 * @param version unique among scripts for that dialect
 * @param resource classpath location, beginning with {@code /}
 */
public record MigrationScript(String dialect, int version, String resource) {}
