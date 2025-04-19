package es.redactado.command.handler;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import com.google.inject.Injector;
import es.redactado.command.*;
import es.redactado.command.type.BaseMessageContextCommand;
import es.redactado.command.type.BaseSlashCommand;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Inspired on ZeyoYT CommandRegister used on AiLama.
 */
public class CommandRegister {

    private final HashMap<String, BaseSlashCommand> slashCommands;
    private final HashMap<String, BaseMessageContextCommand> messageContextCommands;
    private final Cache<String, List<SlashCommandData>> slashCommandDataCache;
    private final Cache<String, List<CommandData>> contextCommandDataCache;
    private final Cache<String, List<CommandData>> allCommandsDataCache;

    @Inject
    public CommandRegister(Injector injector) {
        this.slashCommands = new HashMap<>();
        this.messageContextCommands = new HashMap<>();

        this.slashCommandDataCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100)
                .build();

        this.contextCommandDataCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100)
                .build();

        this.allCommandsDataCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100)
                .build();

        addCommand(injector.getInstance(PingCommand.class));
    }

    public BaseSlashCommand getSlashCommand(String name) {
        return this.slashCommands.get(name);
    }

    public BaseMessageContextCommand getUserContextCommand(String name) {
        return this.messageContextCommands.get(name);
    }

    public List<SlashCommandData> getCommandsSlashData() {
        return slashCommandDataCache.get("slashCommands", key -> {
            List<SlashCommandData> commands = new ArrayList<>();
            for (BaseSlashCommand command : this.slashCommands.values()) {
                commands.add(command.getCommandData());
            }
            return commands;
        });
    }

    public List<CommandData> getContextCommandsData() {
        return contextCommandDataCache.get("contextCommands", key -> {
            List<CommandData> commands = new ArrayList<>();
            for (BaseMessageContextCommand command : this.messageContextCommands.values()) {
                commands.add(command.getCommandData());
            }
            return commands;
        });
    }

    public List<CommandData> getAllCommandsData() {
        return allCommandsDataCache.get("allCommands", key -> {
            List<CommandData> commands = new ArrayList<>();
            commands.addAll(getContextCommandsData());
            commands.addAll(getCommandsSlashData());
            return commands;
        });
    }

    public HashMap<String, BaseSlashCommand> getSlashCommandMap() {
        return this.slashCommands;
    }

    public HashMap<String, BaseMessageContextCommand> getMessageContextCommandMap() {
        return this.messageContextCommands;
    }

    public void addCommand(BaseSlashCommand command) {
        String commandName = command.getCommandData().getName();
        if (this.slashCommands.containsKey(commandName)) {
            throw new IllegalArgumentException("Command with name " + commandName + " already exists");
        }
        this.slashCommands.put(commandName, command);
        invalidateCache();
    }

    public void addCommand(BaseMessageContextCommand command) {
        String commandName = command.getCommandData().getName();
        if (this.messageContextCommands.containsKey(commandName)) {
            throw new IllegalArgumentException("Command with name " + commandName + " already exists");
        }
        this.messageContextCommands.put(commandName, command);
        invalidateCache();
    }

    public List<ListenerAdapter> getListeners() {
        List<ListenerAdapter> listeners = new ArrayList<>();
        for (BaseSlashCommand command : this.slashCommands.values()) {
            if (command instanceof ListenerAdapter) {
                listeners.add((ListenerAdapter) command);
            }
        }
        return listeners;
    }

    private void invalidateCache() {
        slashCommandDataCache.invalidateAll();
        contextCommandDataCache.invalidateAll();
        allCommandsDataCache.invalidateAll();
    }
}
