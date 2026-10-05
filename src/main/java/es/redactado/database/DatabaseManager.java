package es.redactado.database;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import es.redactado.config.BotConfig;
import es.redactado.service.IService;
import es.redactado.service.TaskManager;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the SessionFactory and the one place a transaction is opened.
 *
 * <p>A use case calls {@link #inTransaction} or {@link #read} and does its queries inside that
 * call, so loading a row and writing it back is one commit. {@link #inTransactionAsync} runs
 * that work on {@link TaskManager}'s I/O executor, which is what keeps JDBC off a JDA thread.
 * The Hikari pool, not the virtual threads, is what limits how many queries run at once.
 */
@Singleton
public class DatabaseManager implements IService {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseManager.class);

    private final BotConfig config;
    private final List<Class<?>> entities;
    private final TaskManager taskManager;
    private final Set<MigrationScript> migrations;
    private volatile SessionFactory sessionFactory;

    @Inject
    public DatabaseManager(
            BotConfig config,
            Set<ManagedEntity> entities,
            TaskManager taskManager,
            Set<MigrationScript> migrations) {
        this.config = config;
        List<Class<?>> types = new java.util.ArrayList<>();
        for (ManagedEntity entity : entities) {
            types.add(entity.type());
        }
        this.entities = List.copyOf(types);
        this.taskManager = taskManager;
        this.migrations = Set.copyOf(migrations);
    }

    @Override
    public List<Class<? extends IService>> dependsOn() {
        return List.of(TaskManager.class);
    }

    @Override
    public synchronized void init() {
        if (sessionFactory != null) {
            return;
        }
        Migrations.migrate(config, migrations);
        sessionFactory = HibernateFactory.create(config, entities);
        LOG.info("Database ready ({}, {} entities)", config.databaseType(), entities.size());
    }

    @Override
    public synchronized void shutdown() {
        SessionFactory current = sessionFactory;
        sessionFactory = null;
        if (current != null) {
            LOG.info("Closing the database");
            current.close();
        }
    }

    public <T> T inTransaction(Function<Session, T> work) {
        return transact(work, false);
    }

    public <T> T read(Function<Session, T> work) {
        return transact(work, true);
    }

    public <T> CompletableFuture<T> inTransactionAsync(Function<Session, T> work) {
        return taskManager.supplyIo(() -> inTransaction(work));
    }

    public <T> CompletableFuture<T> readAsync(Function<Session, T> work) {
        return taskManager.supplyIo(() -> read(work));
    }

    private <T> T transact(Function<Session, T> work, boolean readOnly) {
        SessionFactory factory = sessionFactory;
        if (factory == null) {
            throw new IllegalStateException("DatabaseManager is not running");
        }
        try (Session session = factory.openSession()) {
            if (readOnly) {
                session.setDefaultReadOnly(true);
            }
            Transaction transaction = session.beginTransaction();
            try {
                T result = work.apply(session);
                transaction.commit();
                return result;
            } catch (RuntimeException e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw e;
            }
        }
    }
}
