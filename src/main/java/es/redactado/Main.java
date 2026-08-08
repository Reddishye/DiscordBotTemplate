package es.redactado;

import static es.redactado.LogbackOutputStream.redirectSystemOutToLogger;
import static es.redactado.config.Bot.AUTO_RECONNECT;
import static es.redactado.config.Bot.GATEWAY_INTENTS;
import static es.redactado.config.Listeners.LISTENERS;
import static es.redactado.config.Services.BUSINESS_SERVICES;
import static es.redactado.config.Services.INFRASTRUCTURE_SERVICES;

import com.google.inject.Guice;
import com.google.inject.Injector;
import es.redactado.command.handler.CommandRegister;
import es.redactado.service.ServiceManager;
import io.github.cdimascio.dotenv.Dotenv;
import io.sentry.Sentry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
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

    // Guards against onReady firing multiple times across shards
    private final AtomicBoolean businessServicesStarted = new AtomicBoolean(false);
    // READY event captured async; processed after main-thread init completes
    private volatile ReadyEvent capturedReadyEvent;
    // Set true once main-thread init done — lets late READY events fire immediately
    private volatile boolean initComplete;

    public static void main(String[] args) {
        new Main().run();
    }

    public void run() {
        redirectSystemOutToLogger();

        // ── Phase 1: Build ShardManager — onReady only captures event ──
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        shardManager = buildShardManager(dotenv);
        logger.info("ShardManager built, awaiting Ready event...");

        // ── Phase 2: Guice injector — heavy init (Hibernate etc.) ──
        injector = Guice.createInjector(new BotModule(this, shardManager));

        serviceManager = injector.getInstance(ServiceManager.class);
        commandRegister = injector.getInstance(CommandRegister.class);

        // ── Phase 3: Infrastructure services ──
        serviceManager.startAll(INFRASTRUCTURE_SERVICES);
        logger.info("Infrastructure services started.");

        // ── Phase 4: Register listeners ──
        List<ListenerAdapter> listeners = instantiateListeners();
        // Also register commands that are listeners (autocomplete, etc)
        commandRegister
                .getListeners()
                .forEach(
                        l -> {
                            if (!listeners.contains(l)) {
                                listeners.add(l);
                            }
                        });
        for (ListenerAdapter listener : listeners) {
            shardManager.addEventListener(listener);
            logger.info("Registered listener: {}", listener.getClass().getSimpleName());
        }

        // ── Phase 5: Mark init complete, process READY if already captured ──
        initComplete = true;
        ReadyEvent re = capturedReadyEvent;
        if (re != null) {
            onBotReady(re);
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

    private void onBotReady(@Nonnull ReadyEvent event) {
        if (!businessServicesStarted.compareAndSet(false, true)) return;

        logger.info("Bot is ready! Connected as {}", event.getJDA().getSelfUser().getAsTag());

        serviceManager.startAll(BUSINESS_SERVICES);
        logger.info("Business services started.");

        Collection<CommandData> commands = commandRegister.getAllCommandsData();
        logger.info("Registering {} commands...", commands.size());
        event.getJDA()
                .updateCommands()
                .addCommands(commands)
                .queue(
                        ok -> logger.info("Commands registered successfully"),
                        err -> logger.error("Failed to register commands: {}", err.getMessage()));
    }

    private ShardManager buildShardManager(Dotenv dotenv) {
        DefaultShardManagerBuilder builder =
                DefaultShardManagerBuilder.createDefault(dotenv.get("DISCORD_TOKEN"))
                        .setAutoReconnect(AUTO_RECONNECT)
                        .enableIntents(GATEWAY_INTENTS)
                        .addEventListeners(
                                new ListenerAdapter() {

                                    @Override
                                    public void onReady(@Nonnull ReadyEvent event) {
                                        capturedReadyEvent = event;
                                        // If main-thread init already done, fire immediately (race
                                        // where READY arrives after init)
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
                                    public void onException(@Nonnull ExceptionEvent event) {
                                        logger.error("Exception in JDA", event.getCause());
                                        Sentry.captureException(event.getCause());
                                    }
                                });

        return builder.build();
    }

    private List<ListenerAdapter> instantiateListeners() {
        List<ListenerAdapter> result = new ArrayList<>();
        for (Class<? extends ListenerAdapter> cls : LISTENERS) {
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
        // Handled inside the connectionListener in buildShardManager
    }
}
