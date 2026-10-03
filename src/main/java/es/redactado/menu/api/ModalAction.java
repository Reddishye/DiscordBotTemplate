package es.redactado.menu.api;

import java.util.Objects;

/**
 * One modal action of a menu: how to acknowledge the submission, and what to run
 * afterwards.
 *
 * @param ack how the router acknowledges the interaction
 * @param handler the work to run once acknowledged
 */
public record ModalAction(Ack ack, ModalHandler handler) {

    public ModalAction {
        Objects.requireNonNull(ack, "ack");
        Objects.requireNonNull(handler, "handler");
    }
}
