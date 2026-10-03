package es.redactado.menu.api;

import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Handles one button interaction. */
@FunctionalInterface
public interface ButtonHandler {

    /**
     * Handles the interaction.
     *
     * @param ctx the context of the current interaction
     * @param event the JDA button event
     * @return a future that completes when the work is done; use
     *     {@link Done#NOW} for a handler with no asynchronous work
     */
    CompletableFuture<Void> handle(MenuContext ctx, ButtonInteractionEvent event);
}
