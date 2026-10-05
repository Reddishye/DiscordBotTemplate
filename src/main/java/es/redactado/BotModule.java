package es.redactado;

import com.google.inject.AbstractModule;
import es.redactado.config.BotConfig;
import es.redactado.config.ConfigFiles;
import es.redactado.config.TemplateBindings;
import es.redactado.database.DatabaseManager;
import es.redactado.feature.BotFeature;
import java.util.ServiceLoader;
import net.dv8tion.jda.api.sharding.ShardManager;

/**
 * Binds the process objects and installs every feature.
 *
 * <p>{@link es.redactado.config.TemplateBindings} is installed by name because it is this
 * repository. Every other {@link BotFeature} comes from {@link ServiceLoader}, which reads
 * {@code META-INF/services/es.redactado.feature.BotFeature}. A file that only contains comments
 * contributes nothing, and that is the state of a fresh checkout.
 */
public class BotModule extends AbstractModule {
    private final Main main;
    private final ShardManager shardManager;
    private final BotConfig config;
    private final ConfigFiles files;

    public BotModule(Main main, ShardManager shardManager, BotConfig config, ConfigFiles files) {
        this.main = main;
        this.shardManager = shardManager;
        this.config = config;
        this.files = files;
    }

    @Override
    protected void configure() {
        bind(Main.class).toInstance(main);
        bind(ShardManager.class).toInstance(shardManager);
        bind(BotConfig.class).toInstance(config);
        bind(ConfigFiles.class).toInstance(files);
        bind(DatabaseManager.class).asEagerSingleton();
        install(new TemplateBindings());
        for (BotFeature feature : ServiceLoader.load(BotFeature.class)) {
            install(feature);
        }
    }
}
