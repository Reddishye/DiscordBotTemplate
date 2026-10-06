package es.redactado.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import es.redactado.command.handler.CommandListener;
import es.redactado.config.BotConfig;
import es.redactado.config.TemplateBindings;
import es.redactado.service.IService;
import es.redactado.service.TaskManager;
import java.util.List;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.junit.jupiter.api.Test;

class FeatureCatalogTest {

    @Test
    void aFeatureAddsToTheTemplateWithoutReplacingIt() {
        FeatureCatalog catalog =
                Guice.createInjector(
                                new AbstractModule() {
                                    @Override
                                    protected void configure() {
                                        bind(BotConfig.class).toInstance(BotConfig.defaults());
                                        install(new TemplateBindings());
                                        install(new ExtraFeature());
                                    }
                                })
                        .getInstance(FeatureCatalog.class);

        assertThat(catalog.services()).contains(TaskManager.class);
        assertThat(catalog.listeners()).contains(CommandListener.class, ExtraListener.class);
        assertThat(catalog.ready()).containsExactly(ExtraService.class);
    }

    private static final class ExtraFeature extends BotFeature {
        @Override
        protected void contribute() {
            listener(ExtraListener.class);
            ready(ExtraService.class);
        }
    }

    public static final class ExtraListener extends ListenerAdapter {}

    public static final class ExtraService implements IService {
        @Override
        public void init() {}

        @Override
        public void shutdown() {}

        @Override
        public List<Class<? extends IService>> dependsOn() {
            return List.of();
        }
    }
}
