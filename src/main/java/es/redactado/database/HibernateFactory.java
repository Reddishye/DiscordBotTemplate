package es.redactado.database;

import es.redactado.config.BotConfig;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.hibernate.tool.schema.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds the SessionFactory from {@link BotConfig}. Does not own its lifecycle. */
final class HibernateFactory {

    private static final Logger LOG = LoggerFactory.getLogger(HibernateFactory.class);

    private HibernateFactory() {}

    static SessionFactory create(BotConfig config, List<Class<?>> entities) {
        String url = JdbcUrls.toUrl(config);
        LOG.info("Configuring database {} at {}", config.databaseType(), url);

        HibernatePersistenceConfiguration configuration =
                new HibernatePersistenceConfiguration("Redactado");
        for (Class<?> entity : entities) {
            configuration.managedClass(entity);
        }

        configuration.jdbcUrl(url);
        configuration.jdbcCredentials(config.dbUser(), config.dbPassword());
        switch (config.databaseType()) {
            case MARIADB -> {
                configuration.property(
                        "hibernate.connection.driver_class", "org.mariadb.jdbc.Driver");
                configuration.property("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
            }
            case SQLITE -> {
                configuration.property("hibernate.connection.driver_class", "org.sqlite.JDBC");
                configuration.property("hibernate.hikari.driverClassName", "org.sqlite.JDBC");
                configuration.property("hibernate.hikari.connectionTestQuery", "SELECT 1");
                configuration.property(
                        "hibernate.hikari.connectionInitSql", "PRAGMA foreign_keys=ON");
                configuration.property(
                        "hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
            }
            case H2 -> {
                configuration.property("hibernate.connection.driver_class", "org.h2.Driver");
                configuration.property("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
                configuration.property(
                        "hibernate.hikari.dataSourceClassName", "org.h2.jdbcx.JdbcDataSource");
                configuration.property("hibernate.hikari.dataSource.url", url);
                configuration.property("hibernate.hikari.dataSource.user", config.dbUser());
                configuration.property("hibernate.hikari.dataSource.password", config.dbPassword());
            }
        }

        BotConfig.PoolSettings pool = config.pool();
        configuration.property(
                "hibernate.connection.provider_class",
                "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
        configuration.property("hibernate.hikari.minimumIdle", Integer.toString(pool.minIdle()));
        configuration.property(
                "hibernate.hikari.maximumPoolSize", Integer.toString(pool.maxSize()));
        configuration.property(
                "hibernate.hikari.idleTimeout", Long.toString(pool.idleTimeoutMillis()));
        configuration.property(
                "hibernate.hikari.maxLifetime", Long.toString(pool.maxLifetimeMillis()));
        configuration.property(
                "hibernate.hikari.connectionTimeout",
                Long.toString(pool.connectionTimeoutMillis()));
        configuration.property(
                "hibernate.hikari.leakDetectionThreshold",
                Long.toString(pool.leakDetectionMillis()));

        configuration.property("hibernate.cache.use_second_level_cache", "true");
        configuration.property("hibernate.cache.use_query_cache", "false");
        configuration.property(
                "hibernate.cache.region.factory_class",
                "org.hibernate.cache.jcache.internal.JCacheRegionFactory");
        configuration.property(
                "hibernate.javax.cache.provider",
                "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider");
        configuration.property("hibernate.javax.cache.missing_cache_strategy", "create");

        configuration.property(
                "hibernate.jdbc.batch_size", Integer.toString(config.hibernate().batchSize()));
        configuration.property("hibernate.order_inserts", "true");
        configuration.property("hibernate.order_updates", "true");
        configuration.property(
                "hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        configuration.property("hibernate.type.preferred_instant_jdbc_type", "TIMESTAMP");

        configuration.showSql(
                config.hibernate().showSql(),
                config.hibernate().formatSql(),
                config.hibernate().highlightSql());
        configuration.schemaToolingAction(
                config.hibernate().schema() == BotConfig.SchemaMode.UPDATE
                        ? Action.UPDATE
                        : Action.VALIDATE);

        return configuration.createEntityManagerFactory();
    }
}
