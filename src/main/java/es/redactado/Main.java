package es.redactado;

import com.google.inject.Guice;
import com.google.inject.Injector;
import es.redactado.command.PingCommand;
import es.redactado.command.handler.CommandListener;
import es.redactado.command.handler.CommandRegister;
import io.github.cdimascio.dotenv.Dotenv;
import io.sentry.Sentry;
import net.dv8tion.jda.api.events.ExceptionEvent;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

public class Main extends ListenerAdapter {
    private Injector injector;
    private ShardManager api;
    private CommandRegister commandRegister;

    public static void main(String[] args) {
        new Main().run();
    }

    public void run() {
        injector = Guice.createInjector(new BotModule(this));

        api = DefaultShardManagerBuilder.createDefault(injector.getInstance(Dotenv.class).get("DISCORD_TOKEN"))
                .setAutoReconnect(true)
                .addEventListeners(new ListenerAdapter() {
                    @Override
                    public void onGenericEvent(@NotNull GenericEvent event) {
                        LoggerFactory.getLogger(this.toString()).debug("Generic event: {}", event);
                    }

                    @Override
                    public void onException(@Nonnull ExceptionEvent event) {
                        Sentry.captureException(event.getCause());
                    }
                })
                .build();

        commandRegister = injector.getInstance(CommandRegister.class);

        api.addEventListener(injector.getInstance(CommandListener.class));

        api.addEventListener(this);

        injector.injectMembers(api);
    }

    @Override
    public void onReady(ReadyEvent event) {
        System.out.println("Logged in as " + event.getJDA().getSelfUser().getAsTag());
        event.getJDA().updateCommands().addCommands(commandRegister.getAllCommandsData()).queue();
        for (ListenerAdapter listener : commandRegister.getListeners()) {
            event.getJDA().addEventListener(listener);
        }
        System.out.println("Commands registered");
    }
}