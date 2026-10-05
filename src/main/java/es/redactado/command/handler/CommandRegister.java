package es.redactado.command.handler;

import com.google.inject.Inject;
import es.redactado.command.type.BaseMessageContextCommand;
import es.redactado.command.type.BaseSlashCommand;
import es.redactado.command.type.BaseUserContextCommand;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** The commands bound by feature modules, indexed by the name Discord will send back. */
public class CommandRegister {

    private final Map<String, BaseSlashCommand> slashCommands;
    private final Map<String, BaseMessageContextCommand> messageContextCommands;
    private final Map<String, BaseUserContextCommand> userContextCommands;
    private final List<CommandData> allCommands;

    @Inject
    public CommandRegister(
            Set<BaseSlashCommand> slash,
            Set<BaseMessageContextCommand> messageContext,
            Set<BaseUserContextCommand> userContext) {
        Map<String, BaseSlashCommand> slashByName = new HashMap<>();
        Map<String, BaseMessageContextCommand> contextByName = new HashMap<>();
        Map<String, BaseUserContextCommand> userByName = new HashMap<>();
        List<CommandData> data = new ArrayList<>();
        for (BaseSlashCommand command : slash) {
            put(slashByName, command.getCommandData().getName(), command);
            data.add(command.getCommandData());
        }
        for (BaseMessageContextCommand command : messageContext) {
            put(contextByName, command.getCommandData().getName(), command);
            data.add(command.getCommandData());
        }
        for (BaseUserContextCommand command : userContext) {
            put(userByName, command.getCommandData().getName(), command);
            data.add(command.getCommandData());
        }
        this.slashCommands = Map.copyOf(slashByName);
        this.messageContextCommands = Map.copyOf(contextByName);
        this.userContextCommands = Map.copyOf(userByName);
        this.allCommands = List.copyOf(data);
    }

    public BaseSlashCommand getSlashCommand(String name) {
        return slashCommands.get(name);
    }

    public BaseMessageContextCommand getMessageContextCommand(String name) {
        return messageContextCommands.get(name);
    }

    public BaseUserContextCommand getUserContextCommand(String name) {
        return userContextCommands.get(name);
    }

    public Map<String, BaseSlashCommand> getSlashCommandMap() {
        return slashCommands;
    }

    public Map<String, BaseMessageContextCommand> getMessageContextCommandMap() {
        return messageContextCommands;
    }

    public List<SlashCommandData> getCommandsSlashData() {
        List<SlashCommandData> commands = new ArrayList<>();
        for (BaseSlashCommand command : slashCommands.values()) {
            commands.add(command.getCommandData());
        }
        return commands;
    }

    public List<CommandData> getAllCommandsData() {
        return allCommands;
    }

    /**
     * Commands that are also listeners, for autocomplete that a command handles itself.
     * The usual path is {@link CommandListener}, which does not need this.
     */
    public List<ListenerAdapter> getListeners() {
        List<ListenerAdapter> listeners = new ArrayList<>();
        for (BaseSlashCommand command : slashCommands.values()) {
            if (command instanceof ListenerAdapter listener) {
                listeners.add(listener);
            }
        }
        return listeners;
    }

    private static <T> void put(Map<String, T> map, String name, T command) {
        if (map.containsKey(name)) {
            throw new IllegalArgumentException("Command with name " + name + " already exists");
        }
        map.put(name, command);
    }

    public List<CommandData> getContextCommandsData() {
        List<CommandData> commands = new ArrayList<>();
        for (BaseMessageContextCommand command : messageContextCommands.values()) {
            commands.add(command.getCommandData());
        }
        return Collections.unmodifiableList(commands);
    }
}
