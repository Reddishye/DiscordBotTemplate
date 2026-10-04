package es.redactado.service;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import es.redactado.exception.service.DependencyResolutionException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ServiceManager {

    private static final Logger logger = LoggerFactory.getLogger(ServiceManager.class);

    private final Injector injector;
    private final List<IService> resolved = new ArrayList<>();

    @Inject
    public ServiceManager(Injector injector) {
        this.injector = injector;
    }

    public void startAll(List<Class<? extends IService>> serviceClasses) {
        // Snapshot before this batch, so only new services are initialised
        List<IService> previouslyResolved = new ArrayList<>(resolved);

        Map<Class<? extends IService>, IService> instances =
                serviceClasses.stream()
                        .collect(
                                Collectors.toMap(
                                        Function.identity(),
                                        injector::getInstance,
                                        (a, b) -> a,
                                        java.util.LinkedHashMap::new));

        // Merge previously resolved instances so cross-batch deps resolve
        for (IService svc : previouslyResolved) {
            instances.putIfAbsent((Class<? extends IService>) svc.getClass(), svc);
        }

        LinkedHashSet<IService> order = new LinkedHashSet<>();
        for (IService service : instances.values()) {
            resolve(service, instances, order, new LinkedHashSet<>());
        }

        resolved.addAll(order);

        // Init only NEW services from this batch
        for (IService service : order) {
            if (previouslyResolved.contains(service)) continue;
            String name = service.getClass().getSimpleName();
            try {
                logger.info("Initializing service: {}", name);
                service.init();
                logger.info("Service initialized: {}", name);
            } catch (Exception e) {
                logger.error("Failed to initialize service: {}", name, e);
                throw new RuntimeException("Service init failed: " + name, e);
            }
        }
    }

    public void stopAll() {
        // Shutdown in reverse init order
        List<IService> reversed = new ArrayList<>(resolved);
        java.util.Collections.reverse(reversed);

        for (IService service : reversed) {
            String name = service.getClass().getSimpleName();
            try {
                logger.info("Shutting down service: {}", name);
                service.shutdown();
                logger.info("Service stopped: {}", name);
            } catch (Exception e) {
                logger.warn("Error during shutdown of service: {}", name, e);
                // Continue shutting down remaining services
            }
        }

        resolved.clear();
    }

    private void resolve(
            IService service,
            Map<Class<? extends IService>, IService> instances,
            LinkedHashSet<IService> resolved,
            LinkedHashSet<IService> visiting) {

        if (resolved.contains(service)) return;

        if (visiting.contains(service)) {
            String cycle =
                    visiting.stream()
                            .map(s -> s.getClass().getSimpleName())
                            .collect(Collectors.joining(" -> "));
            throw new DependencyResolutionException(
                    "Circular dependency detected: "
                            + cycle
                            + " -> "
                            + service.getClass().getSimpleName());
        }

        visiting.add(service);

        for (Class<? extends IService> depClass : service.dependsOn()) {
            IService dep = instances.get(depClass);
            if (dep == null) {
                throw new DependencyResolutionException(
                        "Service '"
                                + service.getClass().getSimpleName()
                                + "' depends on '"
                                + depClass.getSimpleName()
                                + "' but it is not registered.");
            }
            resolve(dep, instances, resolved, visiting);
        }

        visiting.remove(service);
        resolved.add(service);
    }
}
