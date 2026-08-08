package es.redactado.service;

import java.util.List;

public interface IService {

    default List<Class<? extends IService>> dependsOn() {
        return List.of();
    }

    void init();

    void shutdown();
}
