package es.redactado.feature;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import es.redactado.service.IService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * The services and listeners registered by every {@link BotFeature}, including {@code
 * TemplateBindings}.
 *
 * <p>{@code services()} start before the bot connects. {@code ready()} start after Discord sends
 * ready. A class registered twice appears once. {@link es.redactado.service.ServiceManager} starts
 * services in {@link es.redactado.service.IService#dependsOn()} order.
 */
@Singleton
public class FeatureCatalog {

    private final List<Class<? extends IService>> services;
    private final List<Class<? extends IService>> ready;
    private final List<Class<? extends ListenerAdapter>> listeners;

    @Inject
    public FeatureCatalog(
            Set<InfrastructureService> services,
            Set<BusinessService> ready,
            Set<ListenerBinding> listeners) {
        this.services = types(services, InfrastructureService::type);
        this.ready = types(ready, BusinessService::type);
        this.listeners = types(listeners, ListenerBinding::type);
    }

    public List<Class<? extends IService>> services() {
        return services;
    }

    public List<Class<? extends IService>> ready() {
        return ready;
    }

    public List<Class<? extends ListenerAdapter>> listeners() {
        return listeners;
    }

    private static <T, B> List<Class<? extends T>> types(
            Set<B> bindings, Function<B, Class<? extends T>> type) {
        List<Class<? extends T>> found = new ArrayList<>();
        for (B binding : bindings) {
            Class<? extends T> contributed = type.apply(binding);
            if (!found.contains(contributed)) {
                found.add(contributed);
            }
        }
        return List.copyOf(found);
    }
}
