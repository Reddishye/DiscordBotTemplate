package es.redactado.command.type;

import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

/** A command that acts on a user from the context menu. */
public interface BaseUserContextCommand {
    CommandData getCommandData();

    void handle(UserContextInteractionEvent event);
}
