package es.redactado.command.handler;

import com.google.inject.Inject;
import io.sentry.Sentry;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class CommandListener extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandListener.class);
    private final CommandRegister commandRegister;
    private final Executor commandExecutor;

    @Inject
    public CommandListener(CommandRegister commandRegister) {
        this.commandRegister = commandRegister;
        this.commandExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public void onMessageContextInteraction(MessageContextInteractionEvent event) {
        String commandName = event.getInteraction().getName();
        handleCommand(
                commandName,
                "message context",
                () -> commandRegister.getMessageContextCommandMap().containsKey(commandName),
                () -> commandRegister.getUserContextCommand(commandName).handle(event)
        );
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getInteraction().getName();
        handleCommand(
                commandName,
                "slash",
                () -> commandRegister.getSlashCommandMap().containsKey(commandName),
                () -> commandRegister.getSlashCommand(commandName).handle(event)
        );
    }

    private void handleCommand(
            String commandName,
            String commandType,
            Supplier<Boolean> commandExists,
            Runnable commandExecution) {

        LOGGER.debug("Processing {} command: {}", commandType, commandName);

        if (commandExists.get()) {
            LOGGER.info("Executing {} command: {}", commandType, commandName);
            commandExecutor.execute(() -> {
                try {
                    commandExecution.run();
                } catch (Exception e) {
                    Sentry.captureException(e);
                }
            });
        } else {
            LOGGER.info("Command not found: {} (type: {})", commandName, commandType);
        }
    }
}
