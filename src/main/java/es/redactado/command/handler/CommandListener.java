package es.redactado.command.handler;

import com.google.inject.Inject;
import io.sentry.Sentry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandListener extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandListener.class);
    private static final String ERROR_MESSAGE =
            Character.toString(0x26A0)
                    + "\uFE0F"
                    + " An internal error occurred processing this command.";

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
        var cmd = commandRegister.getMessageContextCommandMap().get(commandName);
        if (cmd == null) return;

        Consumer<String> errorReply =
                msg -> {
                    if (!event.isAcknowledged()) {
                        event.reply(msg).setEphemeral(true).queue();
                    } else {
                        event.getHook().sendMessage(msg).setEphemeral(true).queue();
                    }
                };

        CompletableFuture.runAsync(() -> cmd.handle(event), commandExecutor)
                .whenComplete(
                        (result, error) ->
                                handleError(error, commandName, "message context", errorReply));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getInteraction().getName();
        var cmd = commandRegister.getSlashCommandMap().get(commandName);
        if (cmd == null) return;

        Consumer<String> errorReply =
                msg -> {
                    if (!event.isAcknowledged()) {
                        event.reply(msg).setEphemeral(true).queue();
                    } else {
                        event.getHook().sendMessage(msg).setEphemeral(true).queue();
                    }
                };

        CompletableFuture.runAsync(() -> cmd.handle(event), commandExecutor)
                .whenComplete(
                        (result, error) -> handleError(error, commandName, "slash", errorReply));
    }

    private void handleError(
            Throwable error, String commandName, String type, Consumer<String> reply) {
        if (error == null) return;
        LOGGER.error("{} command '{}' failed", type, commandName, error);
        Sentry.captureException(error);
        try {
            reply.accept(ERROR_MESSAGE);
        } catch (Exception replyError) {
            LOGGER.warn("Failed to send error reply for '{}'", commandName, replyError);
        }
    }
}
