package es.redactado.database.repository;

import es.redactado.database.DatabaseManager;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Optional;

/**
 * Query helpers that run inside {@link DatabaseManager}'s transaction.
 *
 * <p>Each method is one transaction. A use case that must read and write together should call
 * {@link DatabaseManager#inTransaction} itself and use the session, rather than chaining these
 * methods and committing twice.
 */
public abstract class AbstractRepository<T, ID extends Serializable> implements Repository<T, ID> {

    protected final DatabaseManager databaseManager;
    private final Class<T> entityClass;

    @SuppressWarnings("unchecked")
    public AbstractRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.entityClass =
                (Class<T>)
                        ((ParameterizedType) getClass().getGenericSuperclass())
                                .getActualTypeArguments()[0];
    }

    @Override
    public Optional<T> findById(ID id) {
        return databaseManager.read(session -> Optional.ofNullable(session.find(entityClass, id)));
    }

    @Override
    public List<T> findRange(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be 0 or more, got " + offset);
        }
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit must be from 1 to 200, got " + limit);
        }
        return databaseManager.read(
                session ->
                        session.createQuery("from " + entityClass.getSimpleName(), entityClass)
                                .setFirstResult(offset)
                                .setMaxResults(limit)
                                .getResultList());
    }

    @Override
    public T save(T entity) {
        return databaseManager.inTransaction(session -> session.merge(entity));
    }

    @Override
    public void delete(T entity) {
        databaseManager.inTransaction(
                session -> {
                    T managed = session.contains(entity) ? entity : session.merge(entity);
                    session.remove(managed);
                    return null;
                });
    }

    @Override
    public void deleteById(ID id) {
        databaseManager.inTransaction(
                session -> {
                    T entity = session.find(entityClass, id);
                    if (entity != null) {
                        session.remove(entity);
                    }
                    return null;
                });
    }
}
