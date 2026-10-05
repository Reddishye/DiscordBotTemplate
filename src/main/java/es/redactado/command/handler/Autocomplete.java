package es.redactado.command.handler;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;

/** A slash command that also answers autocomplete. {@link CommandListener} calls it. */
public interface Autocomplete {
    void complete(CommandAutoCompleteInteractionEvent event);
}
