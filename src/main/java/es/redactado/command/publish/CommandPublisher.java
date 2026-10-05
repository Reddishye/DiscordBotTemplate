package es.redactado.command.publish;

import es.redactado.config.BotConfig;
import java.util.Collection;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Upserts slash and context commands once the gateway is ready. */
public final class CommandPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(CommandPublisher.class);

    private CommandPublisher() {}

    public static void publish(
            ShardManager shards,
            JDA readyShard,
            BotConfig config,
            Collection<CommandData> commands) {
        if (commands.isEmpty()) {
            LOG.info("No commands to register");
            return;
        }
        if (config.commandScope() == BotConfig.CommandScope.GUILD) {
            publishToGuild(shards, config.commandGuildId(), commands);
            return;
        }
        JDA shard = shards.getShardById(0);
        if (shard == null) {
            shard = readyShard;
        }
        LOG.info("Registering {} global commands", commands.size());
        shard.updateCommands()
                .addCommands(commands)
                .queue(
                        ok -> LOG.info("Commands registered"),
                        err -> LOG.error("Failed to register commands: {}", err.getMessage()));
    }

    private static void publishToGuild(
            ShardManager shards, long guildId, Collection<CommandData> commands) {
        if (guildId == 0L) {
            LOG.warn(
                    "commands.scope is GUILD and commands.guildId is 0, so no commands were"
                            + " registered");
            return;
        }
        Guild guild = shards.getGuildById(guildId);
        if (guild == null) {
            LOG.error(
                    "commands.guildId {} is not in the guild cache, so no commands were registered",
                    guildId);
            return;
        }
        LOG.info("Registering {} commands on guild {}", commands.size(), guildId);
        guild.updateCommands()
                .addCommands(commands)
                .queue(
                        ok -> LOG.info("Commands registered on guild {}", guildId),
                        err -> LOG.error("Failed to register commands: {}", err.getMessage()));
    }
}
