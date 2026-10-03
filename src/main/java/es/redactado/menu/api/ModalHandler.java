package es.redactado.menu.api;

import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;

/** Handles one modal submission. */
@FunctionalInterface
public interface ModalHandler {

    /**
     * Handles the submission.
     *
     * @param ctx the context of the current interaction
     * @param event the JDA modal event
     * @return a future that completes when the work is done; use
     *     {@link Done#NOW} for a handler with no asynchronous work
     */
    CompletableFuture<Void> handle(MenuContext ctx, ModalInteractionEvent event);
}
