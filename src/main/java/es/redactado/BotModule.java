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
 * Binds the process and installs features.
 *
 * <p>{@link es.redactado.config.TemplateBindings} is this repository. Further {@link BotFeature}
 * classes are read from {@code META-INF/services/es.redactado.feature.BotFeature}.
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
