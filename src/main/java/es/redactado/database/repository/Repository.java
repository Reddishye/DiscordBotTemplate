package es.redactado.database.repository;

import java.util.List;
import java.util.Optional;

public interface Repository<T, ID> {
    T save(T entity);

    Optional<T> findById(ID id);

    /**
     * A bounded page. {@code limit} is at most 200 so a caller cannot pull the table into memory
     * by accident.
     */
    List<T> findRange(int offset, int limit);

    void delete(T entity);

    void deleteById(ID id);
}
