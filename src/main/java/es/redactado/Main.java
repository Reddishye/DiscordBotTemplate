package es.redactado;

import com.google.inject.Guice;
import com.google.inject.Injector;
import es.redactado.command.handler.CommandListener;
import es.redactado.command.handler.CommandRegister;
import io.github.cdimascio.dotenv.Dotenv;
import io.sentry.Sentry;
import javax.annotation.Nonnull;
import net.dv8tion.jda.api.events.ExceptionEvent;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static es.redactado.LogbackOutputStream.redirectSystemOutToLogger;
import static es.redactado.config.Bot.AUTO_RECONNECT;
import static es.redactado.config.Bot.GATEWAY_INTENTS;
import static es.redactado.config.Listeners.LISTENERS;

public class Main extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private Injector injector;
    private ShardManager api;
    private CommandRegister commandRegister;

    public static void main(String[] args) {
        new Main().run();
    }

    public void run() {
        /*
          System.out is not intended to be used in a production environment; however,
          I understand that it is useful for debugging purposes, so to avoid
          it from breaking the style of the logs, it is redirected to the logger.

          Do not use System.out in production code, use the logger instead or a Sentry capture.
         */
        redirectSystemOutToLogger();

        // 2. Create the Guice injector and get CommandRegister early
        injector = Guice.createInjector(new BotModule(this));
        commandRegister = injector.getInstance(CommandRegister.class);

        // 3. Pre-instantiate all listeners
        List<ListenerAdapter> readyListeners = new ArrayList<>();
        for (Class<? extends ListenerAdapter> cls : LISTENERS) {
            try {
                ListenerAdapter listener = injector.getInstance(cls);
                readyListeners.add(listener);
                logger.info("Initializing listener: {}", cls.getSimpleName());
            } catch (Exception e) {
                logger.error("Failed to instantiate listener: {}", cls.getSimpleName(), e);
            }
        }

        // 4. Dedicated connection listener to register commands on Ready
        ListenerAdapter connectionListener = new ListenerAdapter() {
            @Override
            public void onReady(@Nonnull ReadyEvent event) {
                logger.info("Bot is ready! Connected as {}", event.getJDA().getSelfUser().getAsTag());
                logger.info("Registering commands...");
                Collection<CommandData> commands = commandRegister.getAllCommandsData();
                logger.info("Registering a total of {} commands", commands.size());
                event.getJDA()
                        .updateCommands()
                        .addCommands(commands)
                        .queue(
                                success -> logger.info("Commands registered successfully"),
                                error   -> logger.error("Failed to register commands: {}", error.getMessage())
                        );
            }

            @Override
            public void onGenericEvent(@NotNull GenericEvent event) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Received event: {}", event.getClass().getSimpleName());
                }
            }

            @Override
            public void onException(@Nonnull ExceptionEvent event) {
                logger.error("Exception in JDA", event.getCause());
                Sentry.captureException(event.getCause());
            }
        };

        // 5. Build ShardManager: token, reconnect, intents, add connectionListener
        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder
                .createDefault(injector.getInstance(Dotenv.class).get("DISCORD_TOKEN"))
                .setAutoReconnect(AUTO_RECONNECT)
                .enableIntents(GATEWAY_INTENTS)
                .addEventListeners(connectionListener);

        // 6. Register all pre-instantiated listeners
        for (ListenerAdapter listener : readyListeners) {
            builder.addEventListeners(listener);
            logger.info("Registered listener: {}", listener.getClass().getSimpleName());
        }

        // 7. Build the ShardManager and log startup
        api = builder.build();
        logger.info("Bot is starting up...");
    }

    @Override
    public void onReady(ReadyEvent event) {
        // No additional handling here; all command registration is done in connectionListener
    }
}
