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
        // Defer the reply to show "Bot is thinking..." indicator
        event.deferReply().queue();

        // Measure the time it takes for the API to process the command
        long startTime = System.currentTimeMillis();

        // Calculate response time
        long responseTime = System.currentTimeMillis() - startTime;

        // Get the gateway ping (WebSocket connection latency)
        long gatewayPing = event.getJDA().getGatewayPing();

        // Send a detailed response with both response time and gateway ping
        event.getHook().sendMessage(String.format(
                "`🏓` Pong!\n" +
                        "`⏱️` Response time: %d ms\n" +
                        "`🌐` Gateway ping: %d ms",
                responseTime, gatewayPing)).queue();
    }
}
