package es.redactado.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import java.util.List;

/**
 * The fields of {@code config.yml}.
 *
 * <p>The no-argument constructors are the values written when the file does not exist yet.
 * {@link BotConfig} checks those values and turns the text into the types the bot uses.
 */
@Configuration
public record ConfigFile(
        BotFile bot,
        DatabaseFile database,
        PoolFile pool,
        HibernateFile hibernate,
        MenuFile menu,
        CommandsFile commands,
        SentryFile sentry) {

    public ConfigFile() {
        this(
                new BotFile(),
                new DatabaseFile(),
                new PoolFile(),
                new HibernateFile(),
                new MenuFile(),
                new CommandsFile(),
                new SentryFile());
    }

    @Configuration
    public record BotFile(
            @Comment("Discord bot token. Environment: BOT_TOKEN.") String token,
            @Comment(
                            "Gateway intent names, one per line. Environment: BOT_INTENTS, as a"
                                    + " comma-separated list.")
                    List<String> intents,
            @Comment("Status text. Empty shows no status. Environment: BOT_STATUS.") String status,
            @Comment("Reconnect after the gateway drops. Environment: BOT_AUTO_RECONNECT.")
                    boolean autoReconnect,
            @Comment("Gateway shards to open. 1 is a single process. Environment: BOT_SHARDS.")
                    int shards) {

        public BotFile() {
            this(
                    "change-me",
                    List.of("GUILD_MESSAGES", "GUILD_MESSAGE_REACTIONS", "DIRECT_MESSAGES"),
                    "",
                    true,
                    1);
        }
    }

    @Configuration
    public record DatabaseFile(
            @Comment("SQLITE, MARIADB or H2. Environment: BOT_DATABASE_TYPE.") String type,
            @Comment("MariaDB hostname. Unused for SQLITE and H2. Environment: BOT_DATABASE_HOST.")
                    String host,
            @Comment("MariaDB port. Unused for SQLITE and H2. Environment: BOT_DATABASE_PORT.")
                    int port,
            @Comment("Database name. Environment: BOT_DATABASE_NAME.") String name,
            @Comment("MariaDB user. Unused for SQLITE and H2. Environment: BOT_DATABASE_USER.")
                    String user,
            @Comment("MariaDB password. Environment: BOT_DATABASE_PASSWORD.") String password,
            @Comment("Folder for the SQLITE or H2 file. Environment: BOT_DATABASE_PATH.")
                    String path) {

        public DatabaseFile() {
            this("SQLITE", "localhost", 3306, "redactado", "root", "", "./database");
        }
    }

    @Configuration
    public record PoolFile(
            @Comment(
                            "Connections kept idle. SQLITE always runs with 1. Environment:"
                                    + " BOT_POOL_MIN_IDLE.")
                    int minIdle,
            @Comment(
                            "Maximum open connections. SQLITE always runs with 1. Environment:"
                                    + " BOT_POOL_MAX_SIZE.")
                    int maxSize,
            @Comment(
                            "Milliseconds before an idle connection is closed. Environment:"
                                    + " BOT_POOL_IDLE_TIMEOUT_MILLIS.")
                    long idleTimeoutMillis,
            @Comment(
                            "Milliseconds before a connection is retired. Environment:"
                                    + " BOT_POOL_MAX_LIFETIME_MILLIS.")
                    long maxLifetimeMillis,
            @Comment(
                            "Milliseconds to wait for a connection. Environment:"
                                    + " BOT_POOL_CONNECTION_TIMEOUT_MILLIS.")
                    long connectionTimeoutMillis,
            @Comment(
                            "Milliseconds a borrowed connection may be held before a warning."
                                    + " Environment: BOT_POOL_LEAK_DETECTION_MILLIS.")
                    long leakDetectionMillis) {

        public PoolFile() {
            this(1, 10, 30_000L, 1_800_000L, 30_000L, 60_000L);
        }
    }

    @Configuration
    public record HibernateFile(
            @Comment("Log SQL. Environment: BOT_HIBERNATE_SHOW_SQL.") boolean showSql,
            @Comment("Indent logged SQL. Environment: BOT_HIBERNATE_FORMAT_SQL.") boolean formatSql,
            @Comment("Color logged SQL. Environment: BOT_HIBERNATE_HIGHLIGHT_SQL.")
                    boolean highlightSql,
            @Comment("Rows per JDBC batch. Environment: BOT_HIBERNATE_BATCH_SIZE.") int batchSize,
            @Comment(
                            "VALIDATE runs the SQL files, then checks the tables. UPDATE skips the"
                                    + " SQL files and lets Hibernate change the database."
                                    + " Environment: BOT_HIBERNATE_SCHEMA.")
                    String schema) {

        public HibernateFile() {
            this(false, false, false, 50, "VALIDATE");
        }
    }

    @Configuration
    public record MenuFile(
            @Comment("Folder of JSON preset files. Environment: BOT_MENU_PRESETS_DIRECTORY.")
                    String presetsDirectory,
            @Comment(
                            "Maximum menu sessions kept in memory. Environment:"
                                    + " BOT_MENU_SESSION_MAX_SIZE.")
                    long sessionMaxSize,
            @Comment(
                            "How long an idle session is kept: 30m, 90s, 2h, 500ms, or a number of"
                                    + " minutes. Environment: BOT_MENU_SESSION_IDLE_TTL.")
                    String sessionIdleTtl,
            @Comment(
                            "Allow a user preset to override the guild preset. Environment:"
                                    + " BOT_MENU_USER_PRESETS_ENABLED.")
                    boolean userPresetsEnabled,
            @Comment(
                            "Preset id used when nobody has chosen one. Environment:"
                                    + " BOT_MENU_DEFAULT_PRESET.")
                    String defaultPreset,
            @Comment(
                            "Menu handlers allowed to run at the same time. 0 does not limit them."
                                    + " Environment: BOT_MENU_MAX_IN_FLIGHT.")
                    int maxInFlight) {

        public MenuFile() {
            this("presets", 50_000L, "30m", false, "default", 0);
        }
    }

    @Configuration
    public record CommandsFile(
            @Comment(
                            "GUILD registers commands on one server. GLOBAL registers them for"
                                    + " every server. Environment: BOT_COMMANDS_SCOPE.")
                    String scope,
            @Comment(
                            "Server id used when scope is GUILD. 0 registers nothing and logs that."
                                    + " Environment: BOT_COMMANDS_GUILD_ID.")
                    long guildId,
            @Comment(
                            "Cooldown used by commands that do not set their own. 0s means no"
                                    + " cooldown. Environment: BOT_COMMANDS_DEFAULT_COOLDOWN.")
                    String defaultCooldown) {

        public CommandsFile() {
            this("GUILD", 0L, "0s");
        }
    }

    @Configuration
    public record SentryFile(
            @Comment("Send errors to Sentry. Environment: BOT_SENTRY_ENABLED.") boolean enabled,
            @Comment("Sentry DSN. Environment: BOT_SENTRY_DSN.") String dsn) {

        public SentryFile() {
            this(false, "");
        }
    }
}
