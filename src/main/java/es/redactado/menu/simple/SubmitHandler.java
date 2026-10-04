package es.redactado.menu.simple;

import java.util.concurrent.CompletableFuture;

/**
 * What a submitted form does with its answers.
 *
 * <p>Menu-wide rather than per view: a form is opened from one view and submitted while the
 * message may be showing another.
 */
@FunctionalInterface
public interface SubmitHandler {

    /**
     * Handles the submission.
     *
     * @param submit the submission, with the context and the answers
     * @return a future completing when the handler is done; may block
     */
    CompletableFuture<Void> handle(Submit submit);
}
