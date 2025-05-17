package es.redactado.config;

import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.List;

public class Bot {
    public static final boolean AUTO_RECONNECT = true;

    public static final List<GatewayIntent> GATEWAY_INTENTS = List.of(
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.GUILD_MESSAGE_REACTIONS,
            GatewayIntent.GUILD_VOICE_STATES,
            GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.GUILD_PRESENCES,
            GatewayIntent.DIRECT_MESSAGES
    );
}
