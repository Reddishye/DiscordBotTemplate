package es.redactado.menu.api;

import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

/**
 * Handles one submission of a string select menu.
 *
 * <p>Separate from {@link ButtonHandler} because the event type differs, and a handler
 * that accepted both would be able to read a select as if it were a button. The selected
 * values are on the event, in the order the user chose them.
 */
@FunctionalInterface
public interface SelectHandler {

    /**
     * Runs the menu's work for the chosen values.
     *
     * @param ctx the context of the current interaction
     * @param event the JDA select event, carrying the chosen values
     * @return a future for the work; the router waits for it before releasing the
     *     interaction
     */
    CompletableFuture<Void> handle(MenuContext ctx, StringSelectInteractionEvent event);
}
