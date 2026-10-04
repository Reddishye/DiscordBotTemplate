package es.redactado.menu.simple;

import java.util.concurrent.CompletableFuture;

/**
 * What a button does.
 *
 * <p>One method and one return type on purpose: several overloads would make a lambda
 * ambiguous at the call site, and a handler that may need to block should be saying so.
 */
@FunctionalInterface
public interface ClickHandler {

    /**
     * Handles the press.
     *
     * @param click the click, with the context and what it can do
     * @return a future completing when the handler is done; may block
     */
    CompletableFuture<Void> handle(Click click);
}
