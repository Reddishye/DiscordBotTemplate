package es.redactado.command.type;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public interface BaseSlashCommand {
    SlashCommandData getCommandData();

    void handle(SlashCommandInteractionEvent event);
}
