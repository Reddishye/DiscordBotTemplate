package es.redactado.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import java.util.List;

/**
 * The YAML document ConfigLib reads and writes.
 *
 * <p>Field names are the keys in {@code config.yml}. Defaults live in the no-argument
 * constructors, which ConfigLib calls when the file does not exist yet. Validation of those
 * values happens later, in {@link BotConfig}, so a broken file fails with the name of the
 * setting rather than with a reflection error.
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
            @Comment("Bot token. Prefer BOT_TOKEN in the environment when running under Compose.")
                    String token,
            @Comment("Gateway intents. Privileged intents are left out of the default.")
                    List<String> intents,
            @Comment("Presence text. Empty means no presence is set.") String status,
            @Comment("Reconnect after a gateway drop.") boolean autoReconnect,
            @Comment("How many gateway shards to open. 1 is enough until Discord asks for more.")
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
            @Comment("SQLITE, MARIADB, or H2.") String type,
            String host,
            int port,
            String name,
            String user,
            @Comment("Prefer BOT_DATABASE_PASSWORD in the environment.") String password,
            @Comment("Directory for SQLITE and H2 files.") String path) {

        public DatabaseFile() {
            this("SQLITE", "localhost", 3306, "redactado", "root", "", "./database");
        }
    }

    @Configuration
    public record PoolFile(
            int minIdle,
            @Comment("SQLITE is forced to 1, because it serializes writes.") int maxSize,
            long idleTimeoutMillis,
            long maxLifetimeMillis,
            long connectionTimeoutMillis,
            long leakDetectionMillis) {

        public PoolFile() {
            this(1, 10, 30_000L, 1_800_000L, 30_000L, 60_000L);
        }
    }

    @Configuration
    public record HibernateFile(
            boolean showSql,
            boolean formatSql,
            boolean highlightSql,
            @Comment("JDBC batch size for inserts and updates.") int batchSize,
            @Comment(
                            "VALIDATE runs migrations then checks the schema. UPDATE is for a"
                                    + " throwaway local file.")
                    String schema) {

        public HibernateFile() {
            this(false, false, false, 50, "VALIDATE");
        }
    }

    @Configuration
    public record MenuFile(
            String presetsDirectory,
            long sessionMaxSize,
            @Comment("Duration: 30m, 90s, 2h, 500ms, or a bare number of minutes.")
                    String sessionIdleTtl,
            boolean userPresetsEnabled,
            String defaultPreset,
            @Comment("How many menu handlers may run at once. 0 means no cap.") int maxInFlight) {

        public MenuFile() {
            this("presets", 50_000L, "30m", false, "default", 0);
        }
    }

    @Configuration
    public record CommandsFile(
            @Comment("GUILD registers on commands.guildId. GLOBAL registers once, from shard 0.")
                    String scope,
            @Comment("Required when scope is GUILD. 0 skips registration and logs why.")
                    long guildId,
            @Comment("Applied when a command does not set its own. 0s disables cooldowns.")
                    String defaultCooldown) {

        public CommandsFile() {
            this("GUILD", 0L, "0s");
        }
    }

    @Configuration
    public record SentryFile(
            boolean enabled, @Comment("Prefer BOT_SENTRY_DSN in the environment.") String dsn) {

        public SentryFile() {
            this(false, "");
        }
    }
}
