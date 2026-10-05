package es.redactado;

import static es.redactado.LogbackOutputStream.redirectSystemOutToLogger;

import com.google.inject.Guice;
import com.google.inject.Injector;
import es.redactado.command.handler.CommandRegister;
import es.redactado.command.publish.CommandPublisher;
import es.redactado.config.BotConfig;
import es.redactado.config.ConfigFiles;
import es.redactado.config.ConfigLoader;
import es.redactado.feature.FeatureCatalog;
import es.redactado.service.ServiceManager;
import io.sentry.Sentry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.ExceptionEvent;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private Injector injector;
    private ShardManager shardManager;
    private ServiceManager serviceManager;
    private CommandRegister commandRegister;
    private FeatureCatalog features;
    private BotConfig config;

    private final AtomicBoolean businessServicesStarted = new AtomicBoolean(false);
    private volatile ReadyEvent capturedReadyEvent;
    private volatile boolean initComplete;

    public static void main(String[] args) {
        new Main().run();
    }

    public void run() {
        redirectSystemOutToLogger();

        Path configFile =
                Path.of(
                        System.getenv("CONFIG_FILE") == null
                                        || System.getenv("CONFIG_FILE").isBlank()
                                ? "config.yml"
                                : System.getenv("CONFIG_FILE"));
        config = ConfigLoader.load(configFile, System.getenv());
        if (config.tokenIsPlaceholder()) {
            throw new IllegalStateException(
                    "Set bot.token in config.yml, or BOT_TOKEN in the environment, before"
                            + " starting");
        }
        if (config.sentryEnabled() && !config.sentryDsn().isBlank()) {
            Sentry.init(options -> options.setDsn(config.sentryDsn()));
        }

        shardManager = buildShardManager(config);
        logger.info("ShardManager built, awaiting Ready event...");

        injector =
                Guice.createInjector(
                        new BotModule(this, shardManager, config, ConfigFiles.beside(configFile)));

        serviceManager = injector.getInstance(ServiceManager.class);
        commandRegister = injector.getInstance(CommandRegister.class);
        features = injector.getInstance(FeatureCatalog.class);

        serviceManager.startAll(features.infrastructure());
        logger.info("Infrastructure services started.");

        List<ListenerAdapter> listeners = instantiateListeners(features.listeners());
        commandRegister
                .getListeners()
                .forEach(
                        listener -> {
                            if (!listeners.contains(listener)) {
                                listeners.add(listener);
                            }
                        });
        for (ListenerAdapter listener : listeners) {
            shardManager.addEventListener(listener);
            logger.info("Registered listener: {}", listener.getClass().getSimpleName());
        }

        initComplete = true;
        ReadyEvent ready = capturedReadyEvent;
        if (ready != null) {
            onBotReady(ready);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        logger.info("Bot is starting up...");
    }

    private void shutdown() {
        logger.info("Shutting down bot...");
        if (serviceManager != null) {
            serviceManager.stopAll();
        }
        if (shardManager != null) {
            shardManager.shutdown();
        }
        logger.info("Shutdown complete.");
    }

    private void onBotReady(ReadyEvent event) {
        if (!businessServicesStarted.compareAndSet(false, true)) {
            return;
        }

        logger.info("Bot is ready! Connected as {}", event.getJDA().getSelfUser().getAsTag());
        if (!config.status().isBlank()) {
            event.getJDA().getPresence().setActivity(Activity.playing(config.status()));
        }

        serviceManager.startAll(features.business());
        logger.info("Business services started.");

        Collection<CommandData> commands = commandRegister.getAllCommandsData();
        CommandPublisher.publish(shardManager, event.getJDA(), config, commands);
    }

    private ShardManager buildShardManager(BotConfig config) {
        DefaultShardManagerBuilder builder =
                DefaultShardManagerBuilder.createDefault(config.token())
                        .setAutoReconnect(config.autoReconnect())
                        .enableIntents(config.intents())
                        .addEventListeners(
                                new ListenerAdapter() {

                                    @Override
                                    public void onReady(ReadyEvent event) {
                                        capturedReadyEvent = event;
                                        if (initComplete) {
                                            onBotReady(event);
                                        }
                                    }

                                    @Override
                                    public void onGenericEvent(@NotNull GenericEvent event) {
                                        if (logger.isTraceEnabled()) {
                                            logger.trace(
                                                    "Received event: {}",
                                                    event.getClass().getSimpleName());
                                        }
                                    }

                                    @Override
                                    public void onException(ExceptionEvent event) {
                                        logger.error("Exception in JDA", event.getCause());
                                        if (config.sentryEnabled()) {
                                            Sentry.captureException(event.getCause());
                                        }
                                    }
                                });

        // One shard is the default. Setting the total only when asked keeps a normal bot on
        // JDA's single-shard builder instead of a sharded login it does not need.
        if (config.shards() > 1) {
            builder.setShardsTotal(config.shards());
        }
        return builder.build();
    }

    private List<ListenerAdapter> instantiateListeners(
            List<Class<? extends ListenerAdapter>> types) {
        List<ListenerAdapter> result = new ArrayList<>();
        for (Class<? extends ListenerAdapter> cls : types) {
            try {
                result.add(injector.getInstance(cls));
                logger.info("Instantiated listener: {}", cls.getSimpleName());
            } catch (Exception e) {
                logger.error("Failed to instantiate listener: {}", cls.getSimpleName(), e);
            }
        }
        return result;
    }

    @Override
    public void onReady(ReadyEvent event) {
        // Handled by the listener installed in buildShardManager.
    }
}
