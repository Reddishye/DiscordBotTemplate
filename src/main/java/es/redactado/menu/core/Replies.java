package es.redactado.menu.core;

import java.util.Objects;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

/**
 * The single place that sends an ephemeral reply.
 *
 * <p>An interaction can be acknowledged exactly once, so after the router has
 * acknowledged it a direct {@code reply} call fails. Every message therefore goes
 * through the interaction hook once the interaction is acknowledged, and through
 * the interaction itself before that.
 */
final class Replies {

    static final String UNKNOWN_ACTION = "Unknown action.";
    static final String ERROR = "Error.";

    private Replies() {}

    /**
     * Sends an ephemeral message, choosing the route that is still legal.
     *
     * @param event the interaction that triggered the message
     * @param text the message text
     */
    static void ephemeral(IReplyCallback event, String text) {
        Objects.requireNonNull(event, "event");
        if (event.isAcknowledged()) {
            event.getHook().sendMessage(text).setEphemeral(true).queue();
        } else {
            event.reply(text).setEphemeral(true).queue();
        }
    }
}
