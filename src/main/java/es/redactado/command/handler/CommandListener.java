package es.redactado.command.handler;

import com.google.inject.Inject;
import es.redactado.command.dispatch.CommandDispatcher;
import es.redactado.command.type.BaseMessageContextCommand;
import es.redactado.command.type.BaseSlashCommand;
import es.redactado.command.type.BaseUserContextCommand;
import es.redactado.config.BotConfig;
import es.redactado.service.TaskManager;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class CommandListener extends ListenerAdapter {

    private final CommandRegister commandRegister;
    private final TaskManager taskManager;
    private final BotConfig config;
    private volatile CommandDispatcher dispatcher;

    @Inject
    public CommandListener(
            CommandRegister commandRegister, TaskManager taskManager, BotConfig config) {
        this.commandRegister = commandRegister;
        this.taskManager = taskManager;
        this.config = config;
    }

    private CommandDispatcher dispatcher() {
        CommandDispatcher current = dispatcher;
        if (current == null) {
            current = new CommandDispatcher(taskManager.ioExecutor(), config.defaultCooldown());
            dispatcher = current;
        }
        return current;
    }

    @Override
    public void onMessageContextInteraction(MessageContextInteractionEvent event) {
        BaseMessageContextCommand command =
                commandRegister.getMessageContextCommandMap().get(event.getName());
        if (command == null) {
            return;
        }
        dispatcher().message(event, command);
    }

    @Override
    public void onUserContextInteraction(UserContextInteractionEvent event) {
        BaseUserContextCommand command = commandRegister.getUserContextCommand(event.getName());
        if (command == null) {
            return;
        }
        dispatcher().user(event, command);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        BaseSlashCommand command = commandRegister.getSlashCommandMap().get(event.getName());
        if (command == null) {
            return;
        }
        dispatcher().slash(event, command);
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        BaseSlashCommand command = commandRegister.getSlashCommand(event.getName());
        if (command instanceof Autocomplete autocomplete) {
            autocomplete.complete(event);
        }
    }
}
