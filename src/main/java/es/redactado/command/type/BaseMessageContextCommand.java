package es.redactado.command.type;

import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

public interface BaseMessageContextCommand {
    CommandData getCommandData();
    void handle(MessageContextInteractionEvent event);
}