package es.redactado.menu.core;

import java.security.SecureRandom;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single place a failed interaction becomes a user-visible message.
 *
 * <p>A {@link es.redactado.menu.api.UserFacingException} carries a message that is
 * meant for the user, so it is shown as-is. Anything else could contain internal
 * detail such as a SQL fragment or a file path, so the user only sees a generic
 * message plus a short reference that ties it to the log entry.
 */
final class ErrorReply {

    private static final Logger LOG = LoggerFactory.getLogger(ErrorReply.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int REFERENCE_LENGTH = 6;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ErrorReply() {}

    /**
     * Reports a failure to the user and the log.
     *
     * @param event the interaction that failed
     * @param error the failure, which may be wrapped
     * @param menuId the menu that failed
     * @param action the action that failed
     */
    static void send(IReplyCallback event, Throwable error, String menuId, String action) {
        Throwable cause = unwrap(error);
        if (cause instanceof es.redactado.menu.api.UserFacingException userFacing) {
            LOG.debug("Action '{}' of menu '{}' rejected", action, menuId, cause);
            reply(event, userFacing.getMessage());
            return;
        }

        String reference = reference();
        LOG.error(
                "Action '{}' of menu '{}' failed (ref {}) in guild {} for user {}",
                action,
                menuId,
                reference,
                guildId(event),
                userId(event),
                cause);
        reply(event, "Something went wrong (ref: %s).".formatted(reference));
    }

    /**
     * Peels wrapper exceptions off so a handler that throws inside a future still
     * reports as the exception it really was.
     */
    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            if (current.getCause() == null) {
                return current;
            }
            current = current.getCause();
        }
        return current;
    }

    /**
     * Delivers the message, tolerating a JDA-side rejection such as an expired
     * hook. A failure to report a failure must not become a second failure, so
     * this is swallowed with a warning rather than propagated into the dispatcher,
     * which is the only place allowed a broad catch.
     */
    private static void reply(IReplyCallback event, String text) {
        try {
            Replies.ephemeral(event, text);
        } catch (RuntimeException failure) {
            LOG.warn("Failed to deliver an error reply to the user", failure);
        }
    }

    private static String reference() {
        char[] out = new char[REFERENCE_LENGTH];
        for (int i = 0; i < REFERENCE_LENGTH; i++) {
            out[i] = HEX[RANDOM.nextInt(HEX.length)];
        }
        return new String(out);
    }

    private static String guildId(IReplyCallback event) {
        return event.getGuild() == null ? "none" : event.getGuild().getId();
    }

    private static String userId(IReplyCallback event) {
        return event.getUser() == null ? "none" : event.getUser().getId();
    }
}
