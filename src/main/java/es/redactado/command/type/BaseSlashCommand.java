package es.redactado.command.type;

import java.time.Duration;
import java.util.Set;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * A slash command.
 *
 * <p>{@link #handle} runs after the dispatcher has acknowledged the interaction, on the I/O
 * executor. Reply through the hook. A null {@link #cooldown()} uses the default from config.
 * Zero disables the limit for this command even when the default is set.
 */
public interface BaseSlashCommand {
    SlashCommandData getCommandData();

    void handle(SlashCommandInteractionEvent event);

    default Duration cooldown() {
        return null;
    }

    default boolean ephemeral() {
        return false;
    }

    default Set<Permission> permissions() {
        return Set.of();
    }
}
