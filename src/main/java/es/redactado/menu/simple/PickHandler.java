package es.redactado.menu.simple;

import java.util.concurrent.CompletableFuture;

/** What a select menu does with the values the user chose. */
@FunctionalInterface
public interface PickHandler {

    /**
     * Handles the choice.
     *
     * @param pick the choice, with the context and the values
     * @return a future completing when the handler is done; may block
     */
    CompletableFuture<Void> handle(Pick pick);
}
