package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import es.redactado.menu.api.UserFacingException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Covers how a failure becomes a message, and what it must never leak. */
class ErrorReplyTest {

    private static final int AWAIT_MS = 5_000;
    private static final String SECRET = "jdbc:mysql://internal-host/db password=hunter2";

    @Test
    @DisplayName("a user-facing exception shows its own message")
    void userFacingMessage() {
        ButtonInteractionEvent event = JdaMocks.button("menu:m:go", true);

        ErrorReply.send(event, new UserFacingException("You need the admin role."), "m", "go");

        assertThat(sent(event)).isEqualTo("You need the admin role.");
    }

    @Test
    @DisplayName("an unexpected exception shows only a generic message with a reference")
    void unexpectedIsGeneric() {
        ButtonInteractionEvent event = JdaMocks.button("menu:m:go", true);

        ErrorReply.send(event, new IllegalStateException(SECRET), "m", "go");

        String sent = sent(event);
        assertThat(sent).startsWith("Something went wrong (ref: ").endsWith(").");
        assertThat(sent).doesNotContain(SECRET).doesNotContain("hunter2");
        assertThat(referenceOf(sent)).matches("[0-9a-f]{6}");
    }

    @Test
    @DisplayName("a CompletionException wrapping a user-facing exception is unwrapped")
    void unwrapsCompletionException() {
        ButtonInteractionEvent event = JdaMocks.button("menu:m:go", true);

        ErrorReply.send(
                event,
                new CompletionException(new UserFacingException("Pick a valid option.")),
                "m",
                "go");

        assertThat(sent(event)).isEqualTo("Pick a valid option.");
    }

    @Test
    @DisplayName("an ExecutionException wrapping a user-facing exception is unwrapped")
    void unwrapsExecutionException() {
        ButtonInteractionEvent event = JdaMocks.button("menu:m:go", true);

        ErrorReply.send(
                event,
                new ExecutionException(new UserFacingException("That profile is closed.")),
                "m",
                "go");

        assertThat(sent(event)).isEqualTo("That profile is closed.");
    }

    @Test
    @DisplayName("two references differ between failures")
    void referencesVary() {
        ButtonInteractionEvent first = JdaMocks.button("menu:m:go", true);
        ButtonInteractionEvent second = JdaMocks.button("menu:m:go", true);

        ErrorReply.send(first, new IllegalStateException(SECRET), "m", "go");
        ErrorReply.send(second, new IllegalStateException(SECRET), "m", "go");

        assertThat(referenceOf(sent(first))).isNotEqualTo(referenceOf(sent(second)));
    }

    @Test
    @DisplayName("an unacknowledged event is answered through reply instead")
    void unacknowledgedUsesReply() {
        ButtonInteractionEvent event = JdaMocks.button("menu:m:go", false);

        ErrorReply.send(event, new UserFacingException("Nope."), "m", "go");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event, timeout(AWAIT_MS)).reply(captor.capture());
        assertThat(captor.getValue()).isEqualTo("Nope.");
    }

    private String sent(ButtonInteractionEvent event) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event.getHook(), timeout(AWAIT_MS)).sendMessage(captor.capture());
        return captor.getValue();
    }

    private static String referenceOf(String message) {
        int start = message.indexOf("ref: ") + "ref: ".length();
        return message.substring(start, message.length() - 2);
    }

    /** Guards against a future edit adding a reply call to the acknowledged path. */
    @Test
    @DisplayName("the acknowledged path does not touch reply")
    void acknowledgedPathUsesHookOnly() {
        ButtonInteractionEvent event = JdaMocks.button("menu:m:go", true);

        ErrorReply.send(event, new IllegalStateException(SECRET), "m", "go");

        sent(event);
        verify(event, org.mockito.Mockito.never()).reply(anyString());
    }
}
