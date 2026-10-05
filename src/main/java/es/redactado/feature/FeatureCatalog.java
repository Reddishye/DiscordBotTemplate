package es.redactado.feature;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import es.redactado.config.Listeners;
import es.redactado.config.Services;
import es.redactado.service.IService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * The services and listeners {@code Main} should start.
 *
 * <p>The template's own lists come first. Features append. A class that was both listed and
 * contributed is kept once, so a feature can mention {@code MenuListener} without starting it
 * twice. Order among services is still {@link es.redactado.service.IService#dependsOn()}, not
 * the order they were registered.
 */
@Singleton
public class FeatureCatalog {

    private final List<Class<? extends IService>> infrastructure;
    private final List<Class<? extends IService>> business;
    private final List<Class<? extends ListenerAdapter>> listeners;

    @Inject
    public FeatureCatalog(
            Set<InfrastructureService> infrastructure,
            Set<BusinessService> business,
            Set<ListenerBinding> listeners) {
        this.infrastructure =
                merge(
                        Services.INFRASTRUCTURE_SERVICES,
                        infrastructure,
                        InfrastructureService::type);
        this.business = merge(Services.BUSINESS_SERVICES, business, BusinessService::type);
        this.listeners = merge(Listeners.LISTENERS, listeners, ListenerBinding::type);
    }

    public List<Class<? extends IService>> infrastructure() {
        return infrastructure;
    }

    public List<Class<? extends IService>> business() {
        return business;
    }

    public List<Class<? extends ListenerAdapter>> listeners() {
        return listeners;
    }

    private static <T, B> List<Class<? extends T>> merge(
            List<Class<? extends T>> base, Set<B> extra, Function<B, Class<? extends T>> type) {
        List<Class<? extends T>> merged = new ArrayList<>(base);
        for (B binding : extra) {
            Class<? extends T> contributed = type.apply(binding);
            if (!merged.contains(contributed)) {
                merged.add(contributed);
            }
        }
        return List.copyOf(merged);
    }
}
