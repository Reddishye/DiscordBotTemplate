package es.redactado.command;

import es.redactado.command.type.BaseSlashCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public class PingCommand implements BaseSlashCommand {

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("ping", "Check if the bot is alive")
                .setNSFW(false)
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        long gatewayPing = event.getJDA().getGatewayPing();
        event.getHook().sendMessage("Pong. Gateway ping: " + gatewayPing + " ms").queue();
    }
}
