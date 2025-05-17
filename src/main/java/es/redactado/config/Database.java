package es.redactado.config;

import es.redactado.database.model.BaseDomain;
import es.redactado.database.repository.AbstractRepository;
import es.redactado.database.repository.Repository;

import java.util.List;

public class Database {
    public static final List<Class<?>> ENTITIES = List.of(
            // Add your entity classes here
    );

    public static final List<Class<? extends AbstractRepository<?, ?>>> REPOSITORIES = List.of(
            // Add your repository classes here
            // They will be injected for later use
    );
}
