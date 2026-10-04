package es.redactado.menu.api;

import java.util.Objects;

/**
 * One button action of a menu: how to acknowledge the interaction, and what to
 * run afterwards.
 *
 * @param ack how the router acknowledges the interaction
 * @param handler the work to run once acknowledged
 */
public record ButtonAction(Ack ack, ButtonHandler handler) {

    public ButtonAction {
        Objects.requireNonNull(ack, "ack");
        Objects.requireNonNull(handler, "handler");
    }
}
