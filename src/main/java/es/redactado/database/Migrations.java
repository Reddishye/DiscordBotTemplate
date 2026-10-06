package es.redactado.database;

import es.redactado.config.BotConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the SQL files for the configured database before Hibernate checks the tables.
 *
 * <p>Files are listed in {@code db/migration/<dialect>/manifest.txt}. A {@link MigrationScript}
 * is added to that list. Each version runs once and is stored in {@code schema_migration}. The
 * same version twice, for the same dialect, stops startup. {@code hibernate.schema: UPDATE}
 * skips this and lets Hibernate change the database.
 */
public final class Migrations {

    private static final Logger LOG = LoggerFactory.getLogger(Migrations.class);

    private Migrations() {}

    public static void migrate(BotConfig config, java.util.Collection<MigrationScript> extra) {
        if (config.hibernate().schema() != BotConfig.SchemaMode.VALIDATE) {
            LOG.warn(
                    "hibernate.schema is UPDATE, so migrations are skipped and Hibernate may alter"
                            + " the database");
            return;
        }
        String dialect =
                switch (config.databaseType()) {
                    case SQLITE -> "sqlite";
                    case MARIADB -> "mariadb";
                    case H2 -> "h2";
                };
        String url = JdbcUrls.toUrl(config);
        try (Connection connection =
                DriverManager.getConnection(url, config.dbUser(), config.dbPassword())) {
            apply(connection, dialect, extra);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not migrate the database", e);
        }
    }

    static void apply(
            Connection connection, String dialect, java.util.Collection<MigrationScript> extra)
            throws SQLException {
        ensureHistory(connection);
        Set<Integer> applied = appliedVersions(connection);
        for (Script script : scripts(dialect, extra)) {
            if (applied.contains(script.version())) {
                continue;
            }
            LOG.info("Applying migration {}", script.name());
            for (String statement : statements(script.sql())) {
                try (Statement jdbc = connection.createStatement()) {
                    jdbc.execute(statement);
                }
            }
            try (var insert =
                    connection.prepareStatement(
                            "insert into schema_migration(version, name, applied_at) values (?, ?,"
                                    + " ?)")) {
                insert.setInt(1, script.version());
                insert.setString(2, script.name());
                insert.setString(3, Instant.now().toString());
                insert.executeUpdate();
            }
        }
    }

    private static void ensureHistory(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    create table if not exists schema_migration (
                      version integer primary key,
                      name varchar(255) not null,
                      applied_at varchar(40) not null
                    )
                    """);
        }
    }

    private static Set<Integer> appliedVersions(Connection connection) throws SQLException {
        Set<Integer> versions = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("select version from schema_migration")) {
            while (rows.next()) {
                versions.add(rows.getInt(1));
            }
        }
        return versions;
    }

    private static List<Script> scripts(
            String dialect, java.util.Collection<MigrationScript> extra) {
        String manifest = read("/db/migration/" + dialect + "/manifest.txt");
        List<Script> scripts = new ArrayList<>();
        for (String line : manifest.split("\n")) {
            String name = line.strip();
            if (name.isEmpty() || name.startsWith("#")) {
                continue;
            }
            int version = versionOf(name);
            scripts.add(new Script(version, name, read("/db/migration/" + dialect + "/" + name)));
        }
        if (extra != null) {
            for (MigrationScript script : extra) {
                if (!dialect.equals(script.dialect())) {
                    continue;
                }
                String name = script.resource();
                int slash = name.lastIndexOf('/');
                scripts.add(
                        new Script(
                                script.version(),
                                slash < 0 ? name : name.substring(slash + 1),
                                read(script.resource())));
            }
        }
        scripts.sort(java.util.Comparator.comparingInt(Script::version));
        Set<Integer> seen = new HashSet<>();
        for (Script script : scripts) {
            if (!seen.add(script.version())) {
                throw new IllegalStateException(
                        "Two migrations for " + dialect + " use version " + script.version());
            }
        }
        return scripts;
    }

    private static int versionOf(String name) {
        if (!name.startsWith("V") || !name.contains("__")) {
            throw new IllegalStateException("Migration names look like V1__words.sql, got " + name);
        }
        try {
            return Integer.parseInt(name.substring(1, name.indexOf("__")));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Migration version is not a number: " + name);
        }
    }

    private static List<String> statements(String sql) {
        List<String> statements = new ArrayList<>();
        for (String part : sql.split(";")) {
            String statement = part.strip();
            if (!statement.isEmpty()) {
                statements.add(statement);
            }
        }
        return statements;
    }

    private static String read(String resource) {
        try (InputStream in = Migrations.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing migration resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + resource, e);
        }
    }

    private record Script(int version, String name, String sql) {}
}
