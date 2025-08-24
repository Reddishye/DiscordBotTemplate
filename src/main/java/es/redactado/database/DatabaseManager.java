package es.redactado.database;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import es.redactado.database.model.*;
import io.github.cdimascio.dotenv.Dotenv;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.hibernate.tool.schema.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static es.redactado.config.Database.ENTITIES;

@Singleton
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private final SessionFactory sessionFactory;

    @Inject
    public DatabaseManager(Dotenv dotenv) {
        logger.info("Initializing DatabaseManager");

        String dbType = dotenv.get("DB_TYPE", "H2").toUpperCase();
        String dbHost = dotenv.get("DB_HOST", "localhost");
        String dbPort = dotenv.get("DB_PORT", "3306");
        String dbName = dotenv.get("DB_NAME", "redactado");
        String dbUser = dotenv.get("DB_USER", "root");
        String dbPassword = dotenv.get("DB_PASSWORD", "");
        String dbPath = dotenv.get("DB_PATH", "./database");

        if (dbType.equals("SQLITE")) {
            java.nio.file.Path dir = java.nio.file.Paths.get(dbPath);
            try {
                java.nio.file.Files.createDirectories(dir);
            } catch (java.io.IOException ex) {
                logger.error("Unable to create directory for database: {}", dir, ex);
                throw new ExceptionInInitializerError("Unable to create directory for database: " + ex.getMessage());
            }
        }

        logger.info("Configuring database connection for type: {}", dbType);

        HibernatePersistenceConfiguration configuration =
                new HibernatePersistenceConfiguration("Redactado");

        // Register entity classes
        for (Class<?> entityClass : ENTITIES) {
            configuration.managedClass(entityClass);
        }

        // Prepare JDBC URL based on a database type
        String jdbcUrl;

        // Configure database connection based on type
        switch (dbType) {
            case "MARIADB":
                jdbcUrl = String.format("jdbc:mariadb://%s:%s/%s", dbHost, dbPort, dbName);
                configuration.jdbcUrl(jdbcUrl);
                configuration.property(
                        "hibernate.connection.driver_class", "org.mariadb.jdbc.Driver");

                configuration.property("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");

                break;
            case "SQLITE":
                jdbcUrl = String.format("jdbc:sqlite:%s/%s.db", dbPath, dbName);
                configuration.jdbcUrl(jdbcUrl);
                configuration.property("hibernate.connection.driver_class", "org.sqlite.JDBC");

                configuration.property("hibernate.hikari.driverClassName", "org.sqlite.JDBC");
                configuration.property("hibernate.hikari.connectionTestQuery", "SELECT 1");
                configuration.property("hibernate.connection.foreign_keys", "true");

                configuration.property(
                        "hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
                break;
            case "H2":
            default:
                // Add AUTO_SERVER=TRUE to allow multiple connections
                jdbcUrl = String.format("jdbc:h2:file:%s/%s;AUTO_SERVER=TRUE", dbPath, dbName);
                configuration.jdbcUrl(jdbcUrl);
                configuration.property("hibernate.connection.driver_class", "org.h2.Driver");

                configuration.property("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
                break;
        }

        configuration.jdbcCredentials(dbUser, dbPassword);

        // Configure a HikariCP connection pool
        configuration.property(
                "hibernate.connection.provider_class",
                "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
        configuration.property("hibernate.hikari.minimumIdle", dotenv.get("HIKARI_MIN_IDLE", "5"));
        configuration.property(
                "hibernate.hikari.maximumPoolSize", dotenv.get("HIKARI_MAX_POOL_SIZE", "10"));
        configuration.property(
                "hibernate.hikari.idleTimeout", dotenv.get("HIKARI_IDLE_TIMEOUT", "30000"));
        configuration.property(
                "hibernate.hikari.maxLifetime", dotenv.get("HIKARI_MAX_LIFETIME", "1800000"));
        configuration.property(
                "hibernate.hikari.connectionTimeout",
                dotenv.get("HIKARI_CONNECTION_TIMEOUT", "30000"));
        configuration.property(
                "hibernate.hikari.leakDetectionThreshold",
                dotenv.get("HIKARI_LEAK_DETECTION", "60000"));

        // For H2 database, use the dataSourceClassName approach
        if (dbType.equals("H2")) {
            configuration.property(
                    "hibernate.hikari.dataSourceClassName", "org.h2.jdbcx.JdbcDataSource");
            configuration.property("hibernate.hikari.dataSource.url", jdbcUrl);
            configuration.property("hibernate.hikari.dataSource.user", dbUser);
            configuration.property("hibernate.hikari.dataSource.password", dbPassword);
        }

        // Configure Caffeine cache
        configuration.property("hibernate.cache.use_second_level_cache", "true");
        configuration.property("hibernate.cache.use_query_cache", "true");
        configuration.property(
                "hibernate.cache.region.factory_class",
                "org.hibernate.cache.jcache.internal.JCacheRegionFactory");
        configuration.property(
                "hibernate.javax.cache.provider",
                "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider");

        // Caffeine specific configuration
        configuration.property("hibernate.javax.cache.missing_cache_strategy", "create");
        configuration.property("hibernate.cache.caffeine.use_blocking", "true");

        // Configure entity-specific cache settings
        configuration.property(
                "hibernate.javax.cache.default_cache",
                "{\"eternal\":false,\"maxSize\":1000,\"timeToIdleSeconds\":300}");
        configuration.property(
                "hibernate.javax.cache.cache.es.redactado.database.model.User",
                "{\"eternal\":false,\"maxSize\":500,\"timeToIdleSeconds\":600}");
        configuration.property(
                "hibernate.javax.cache.cache.es.redactado.database.model.VerificationRequest",
                "{\"eternal\":false,\"maxSize\":200,\"timeToIdleSeconds\":300}");

        // Configure SQL logging
        boolean showSql = Boolean.parseBoolean(dotenv.get("HIBERNATE_SHOW_SQL", "false"));
        boolean formatSql = Boolean.parseBoolean(dotenv.get("HIBERNATE_FORMAT_SQL", "false"));
        boolean highlightSql = Boolean.parseBoolean(dotenv.get("HIBERNATE_HIGHLIGHT_SQL", "false"));
        configuration.showSql(showSql, formatSql, highlightSql);

        // Configure schema generation
        configuration.schemaToolingAction(Action.UPDATE);

        try {
            sessionFactory = configuration.createEntityManagerFactory();
            logger.info(
                    "Hibernate SessionFactory successfully initialized for database type: {}",
                    dbType);
        } catch (Exception e) {
            logger.error("Failed to create SessionFactory", e);
            throw new ExceptionInInitializerError(
                    "Failed to create SessionFactory: " + e.getMessage());
        }
    }

    public Session getSession() {
        logger.debug("Opening new Hibernate session");
        return sessionFactory.openSession();
    }

    public void shutdown() {
        if (sessionFactory != null) {
            logger.info("Shutting down Hibernate SessionFactory");
            sessionFactory.close();
        }
    }
}
