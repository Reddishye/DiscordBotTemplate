package es.redactado.database;

import es.redactado.config.BotConfig;
import java.nio.file.Files;
import java.nio.file.Path;

/** JDBC URL for the configured database. SQLite and H2 directories are created here. */
public final class JdbcUrls {

    private JdbcUrls() {}

    public static String toUrl(BotConfig config) {
        return switch (config.databaseType()) {
            case MARIADB ->
                    "jdbc:mariadb://%s:%d/%s"
                            .formatted(config.dbHost(), config.dbPort(), config.dbName());
            case SQLITE -> {
                ensureDirectory(config.dbPath());
                String file = config.dbPath().resolve(config.dbName() + ".db").toString();
                yield "jdbc:sqlite:" + file + "?journal_mode=WAL&busy_timeout=5000";
            }
            case H2 -> {
                ensureDirectory(config.dbPath());
                yield "jdbc:h2:file:%s/%s;AUTO_SERVER=TRUE"
                        .formatted(config.dbPath(), config.dbName());
            }
        };
    }

    private static void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unable to create directory for database: " + dir, e);
        }
    }
}
