package es.redactado.database.repository;

import es.redactado.database.DatabaseManager;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.Transaction;

public abstract class AbstractRepository<T, ID extends Serializable> {

    protected final DatabaseManager databaseManager;
    private final Class<T> entityClass;

    @SuppressWarnings("unchecked")
    public AbstractRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.entityClass =
                (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass())
                        .getActualTypeArguments()[0];
    }

    public Optional<T> findById(ID id) {
        Session session = null;
        try {
            session = databaseManager.getSession();
            T entity = session.find(entityClass, id);
            return Optional.ofNullable(entity);
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public List<T> findAll() {
        Session session = null;
        try {
            session = databaseManager.getSession();
            return session.createQuery("FROM " + entityClass.getSimpleName(), entityClass).list();
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public T save(T entity) {
        Session session = null;
        Transaction transaction = null;
        try {
            session = databaseManager.getSession();
            transaction = session.beginTransaction();
            T mergedEntity = session.merge(entity);
            transaction.commit();
            return mergedEntity;
        } catch (Exception e) {
            if (transaction != null && transaction.isActive() && session.isOpen()) {
                transaction.rollback();
            }
            throw e;
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public void delete(T entity) {
        Session session = null;
        Transaction transaction = null;
        try {
            session = databaseManager.getSession();
            transaction = session.beginTransaction();
            T managed = session.contains(entity) ? entity : session.merge(entity);
            session.remove(managed);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive() && session.isOpen()) {
                transaction.rollback();
            }
            throw e;
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public void deleteById(ID id) {
        findById(id).ifPresent(this::delete);
    }
}
