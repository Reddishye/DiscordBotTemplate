package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.ButtonHandler;
import es.redactado.menu.api.Done;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.MenuNotFoundException;
import es.redactado.menu.api.ModalHandler;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class MenuRouterTest {

    /** Generous upper bound for the executor to pick a task up. */
    private static final int AWAIT_MS = 5_000;

    private MenuRouter router;
    private ButtonHandler editHandler;
    private ButtonHandler replyHandler;
    private ButtonHandler modalHandler;
    private ButtonHandler noneHandler;
    private ModalHandler submitHandler;

    /** A menu whose actions are supplied by a consumer, so each test can vary them. */
    private static final class TestMenu implements Menu {
        private final Consumer<ActionTable.Builder> declarer;

        TestMenu(Consumer<ActionTable.Builder> declarer) {
            this.declarer = declarer;
        }

        @Override
        public String id() {
            return "test";
        }

        @Override
        public Container render(MenuContext ctx) {
            return Container.of(TextDisplay.of("body"));
        }

        @Override
        public void actions(ActionTable.Builder table) {
            declarer.accept(table);
        }
    }

    @BeforeEach
    void setUp() {
        router = new MenuRouter(MenuExecutor.virtual());
        editHandler = mock(ButtonHandler.class);
        replyHandler = mock(ButtonHandler.class);
        modalHandler = mock(ButtonHandler.class);
        noneHandler = mock(ButtonHandler.class);
        submitHandler = mock(ModalHandler.class);

        when(editHandler.handle(any(), any())).thenReturn(Done.NOW);
        when(replyHandler.handle(any(), any())).thenReturn(Done.NOW);
        when(modalHandler.handle(any(), any())).thenReturn(Done.NOW);
        when(noneHandler.handle(any(), any())).thenReturn(Done.NOW);
        when(submitHandler.handle(any(), any())).thenReturn(Done.NOW);

        router.register(
                "test",
                new TestMenu(
                        table ->
                                table.button("edit", Ack.DEFER_EDIT, editHandler)
                                        .button("reply", Ack.DEFER_REPLY, replyHandler)
                                        .button("modal", Ack.MODAL, modalHandler)
                                        .button("none", Ack.NONE, noneHandler)
                                        .modal("submit", Ack.DEFER_EDIT, submitHandler)));
    }

    @Nested
    @DisplayName("acknowledgement order")
    class AckOrder {

        @Test
        @DisplayName("DEFER_EDIT defers the edit before running the handler")
        void deferEditRunsFirst() {
            ButtonInteractionEvent event = JdaMocks.button("menu:test:edit", false);

            assertThat(router.dispatchButton(event)).isTrue();

            InOrder order = inOrder(event, editHandler);
            order.verify(event).deferEdit();
            order.verify(editHandler, timeout(AWAIT_MS)).handle(any(), any());
            verify(event, never()).deferReply(true);
        }

        @Test
        @DisplayName("DEFER_REPLY defers an ephemeral reply before running the handler")
        void deferReplyRunsFirst() {
            ButtonInteractionEvent event = JdaMocks.button("menu:test:reply", false);

            assertThat(router.dispatchButton(event)).isTrue();

            InOrder order = inOrder(event, replyHandler);
            order.verify(event).deferReply(true);
            order.verify(replyHandler, timeout(AWAIT_MS)).handle(any(), any());
            verify(event, never()).deferEdit();
        }

        @Test
        @DisplayName("MODAL acknowledges nothing")
        void modalAcknowledgesNothing() {
            ButtonInteractionEvent event = JdaMocks.button("menu:test:modal", false);

            assertThat(router.dispatchButton(event)).isTrue();

            verify(modalHandler, timeout(AWAIT_MS)).handle(any(), any());
            verify(event, never()).deferEdit();
            verify(event, never()).deferReply(true);
        }

        @Test
        @DisplayName("NONE acknowledges nothing")
        void noneAcknowledgesNothing() {
            ButtonInteractionEvent event = JdaMocks.button("menu:test:none", false);

            assertThat(router.dispatchButton(event)).isTrue();

            verify(noneHandler, timeout(AWAIT_MS)).handle(any(), any());
            verify(event, never()).deferEdit();
            verify(event, never()).deferReply(true);
        }

        @Test
        @DisplayName("a modal submission defers the edit before running the handler")
        void modalSubmitDefersFirst() {
            ModalInteractionEvent event = JdaMocks.modal("menu:test:submit", false);

            assertThat(router.dispatchModal(event)).isTrue();

            InOrder order = inOrder(event, submitHandler);
            order.verify(event).deferEdit();
            order.verify(submitHandler, timeout(AWAIT_MS)).handle(any(), any());
        }
    }

    @Nested
    @DisplayName("unknown targets")
    class Unknown {

        @Test
        @DisplayName("unknown button action replies once and consumes the event")
        void unknownButtonAction() {
            ButtonInteractionEvent event = JdaMocks.button("menu:test:nope", false);

            assertThat(router.dispatchButton(event)).isTrue();

            verifyNoInteractions(editHandler, replyHandler, modalHandler, noneHandler);
            verify(event, never()).deferEdit();
            verify(event, never()).deferReply(true);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(event, never()).getHook();
            verify(event, timeout(AWAIT_MS)).reply(captor.capture());
            assertThat(captor.getValue()).isEqualTo(Replies.UNKNOWN_ACTION);
        }

        @Test
        @DisplayName("unknown modal action replies once and consumes the event")
        void unknownModalAction() {
            ModalInteractionEvent event = JdaMocks.modal("menu:test:nope", false);

            assertThat(router.dispatchModal(event)).isTrue();

            verifyNoInteractions(submitHandler);
            verify(event, never()).deferEdit();

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(event, timeout(AWAIT_MS)).reply(captor.capture());
            assertThat(captor.getValue()).isEqualTo(Replies.UNKNOWN_ACTION);
        }

        @Test
        @DisplayName("unknown menu id returns false and touches nothing")
        void unknownMenu() {
            ButtonInteractionEvent event = JdaMocks.button("menu:absent:edit", false);

            assertThat(router.dispatchButton(event)).isFalse();

            verifyNoInteractions(editHandler, replyHandler, modalHandler, noneHandler);
            verify(event, never()).reply(anyString());
            verify(event, never()).deferEdit();
        }

        @Test
        @DisplayName("a foreign component id returns false")
        void foreignComponentId() {
            ButtonInteractionEvent event = JdaMocks.button("other:thing:edit", false);

            assertThat(router.dispatchButton(event)).isFalse();

            verify(event, never()).reply(anyString());
        }
    }

    @Nested
    @DisplayName("failures")
    class Failures {

        @Test
        @DisplayName("a synchronous throw on an acknowledged event goes through the hook")
        void synchronousThrowAcknowledged() {
            when(editHandler.handle(any(), any()))
                    .thenThrow(new IllegalStateException("secret internal detail"));
            ButtonInteractionEvent event = JdaMocks.button("menu:test:edit", true);

            assertThat(router.dispatchButton(event)).isTrue();

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(event.getHook(), timeout(AWAIT_MS)).sendMessage(captor.capture());
            verify(event, never()).reply(anyString());
            assertThat(captor.getValue()).doesNotContain("secret internal detail");
        }

        @Test
        @DisplayName("a synchronous throw on an unacknowledged event uses reply")
        void synchronousThrowUnacknowledged() {
            when(noneHandler.handle(any(), any()))
                    .thenThrow(new IllegalStateException("secret internal detail"));
            ButtonInteractionEvent event = JdaMocks.button("menu:test:none", false);

            assertThat(router.dispatchButton(event)).isTrue();

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(event, timeout(AWAIT_MS)).reply(captor.capture());
            assertThat(captor.getValue()).contains("ref:");
        }

        @Test
        @DisplayName("a failed future is reported the same way")
        void failedFuture() {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("secret internal detail"));
            when(editHandler.handle(any(), any())).thenReturn(failed);
            ButtonInteractionEvent event = JdaMocks.button("menu:test:edit", true);

            assertThat(router.dispatchButton(event)).isTrue();

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(event.getHook(), timeout(AWAIT_MS)).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("ref:");
        }

        @Test
        @DisplayName("the error text is the generic message, never the exception text")
        void replyLeaksNothing() {
            when(noneHandler.handle(any(), any()))
                    .thenThrow(new IllegalStateException("secret internal detail"));
            ButtonInteractionEvent event = JdaMocks.button("menu:test:none", false);

            router.dispatchButton(event);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(event, timeout(AWAIT_MS)).reply(captor.capture());
            assertThat(captor.getValue())
                    .startsWith("Something went wrong (ref:")
                    .endsWith(").")
                    .doesNotContain("secret internal detail");
        }
    }

    @AfterEach
    void tearDown() {
        router.close();
    }

    @Nested
    @DisplayName("registration")
    class Registration {

        @Test
        @DisplayName("rejects a duplicate id")
        void rejectsDuplicate() {
            assertThatThrownBy(() -> router.register("test", new TestMenu(table -> {})))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("test");
        }

        @Test
        @DisplayName("rejects an id that does not match the menu's own id")
        void rejectsIdMismatch() {
            assertThatThrownBy(() -> router.register("other", new TestMenu(table -> {})))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("other")
                    .hasMessageContaining("test");
        }

        @Test
        @DisplayName("reports whether an id is registered")
        void reportsRegistration() {
            assertThat(router.isRegistered("test")).isTrue();
            assertThat(router.isRegistered("absent")).isFalse();
        }

        @Test
        @DisplayName("looks a registered menu up")
        void looksUp() {
            assertThat(router.get("test")).isInstanceOf(TestMenu.class);
            assertThatThrownBy(() -> router.get("absent"))
                    .isInstanceOf(MenuNotFoundException.class);
        }
    }
}
