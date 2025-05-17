package es.redactado.config;

import es.redactado.command.PingCommand;
import es.redactado.command.type.BaseMessageContextCommand;
import es.redactado.command.type.BaseSlashCommand;

import java.util.List;
import java.util.function.Consumer;

public class Commands {

    /**
     * This will register the slash commands also as listeners if they extend the ListenerAdapter class.
     */
    public static final boolean REGISTER_LISTENERS = true;

    public static final List<Class<? extends BaseSlashCommand>> SLASH_COMMANDS = List.of(
            PingCommand.class
    );

    public static final List<Class<?>> MESSAGE_CONTEXT_COMMANDS = List.of(
            // Add your message context commands here
    );

    /*
     * CONSUMERS & FUNCTIONS FOR COMMANDS
     *
     * Over this section you will be able to set up functions to execute when a command is loaded.
     */
    public static final Consumer<BaseSlashCommand> SLASH_COMMAND_CONSUMERS = slashCommand -> {
        // Add your custom logic here
    };

    public static final Consumer<BaseMessageContextCommand> MESSAGE_CONTEXT_COMMAND_CONSUMERS = messageContextCommand -> {
        // Add your custom logic here
    };
}
