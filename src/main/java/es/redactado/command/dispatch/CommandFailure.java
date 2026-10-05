package es.redactado.command.dispatch;

/** A failure whose message is meant for the user who ran the command. */
public final class CommandFailure extends RuntimeException {

    public CommandFailure(String message) {
        super(message);
    }
}
