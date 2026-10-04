package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.view.ActionButton;
import es.redactado.menu.view.MenuBuilder;
import es.redactado.menu.view.ModalForm;
import es.redactado.menu.view.Row;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Filling in a form and submitting it, through the router, as a user would.
 *
 * <p>Separate from {@code ModalFormTest} because what is checked here is the pair of
 * rules that only show up in the round trip: a form must be opened on an interaction the
 * router left unacknowledged, and the id it carries must be the one the modal action was
 * declared under. Both halves are satisfied by tests that only look at one of them.
 */
class ModalEndToEndTest {

    private static final int AWAIT_MS = 5_000;
    private static final long MESSAGE = 800L;

    @Test
    @DisplayName("a button declaring Ack.MODAL opens the form and nothing else")
    void buttonOpensTheForm() {
        ApplyMenu menu = new ApplyMenu(Ack.MODAL);
        ButtonInteractionEvent event = click("menu:apply:open");

        try (MenuRouter router = TestRouters.with(menu)) {
            assertThat(router.dispatchButton(event)).isTrue();
            awaitIdle(router);
        }

        assertThat(captured(event).getId())
                .as("the form carries the id of the modal action, not of the button")
                .isEqualTo("menu:apply:submit:42");
        assertThat(captured(event).getTitle()).isEqualTo("Apply");
        verify(event, never()).deferEdit();
        verify(event, never()).deferReply(true);
    }

    @Test
    @DisplayName("the submission reaches the modal action and its answers are readable")
    void submissionReachesTheModalAction() {
        ApplyMenu menu = new ApplyMenu(Ack.MODAL);
        ButtonInteractionEvent open = click("menu:apply:open");
        ModalInteractionEvent submit = submission("Ada", "Because");

        try (MenuRouter router = TestRouters.with(menu)) {
            assertThat(router.dispatchButton(open)).isTrue();
            awaitIdle(router);
            assertThat(router.dispatchModal(submit)).isTrue();
            awaitIdle(router);
        }

        assertThat(menu.answers.get())
                .containsExactlyInAnyOrderEntriesOf(Map.of("name", "Ada", "why", "Because"));
    }

    @Test
    @DisplayName("a form behind a deferred edit cannot be opened, because the reply is taken")
    void deferredEditLeavesNoRoomForAModal() {
        ApplyMenu menu = new ApplyMenu(Ack.DEFER_EDIT);
        // Already acknowledged, which is the state the router leaves an interaction in
        // once it has deferred the edit. That is what makes the reply unavailable.
        ButtonInteractionEvent event =
                JdaMocks.button("menu:apply:open", true, MESSAGE, JdaMocks.NO_OWNER);

        try (MenuRouter router = TestRouters.with(menu)) {
            assertThat(router.dispatchButton(event)).isTrue();
            awaitIdle(router);
        }

        verify(event).deferEdit();
        verify(event, never()).replyModal(any(Modal.class));
        assertThat(menu.failed.get()).as("the handler is told why, not left wondering").isTrue();
    }

    // ------------------------------------------------------------- the menus

    /**
     * A menu with a button that opens a form and a modal action that reads it.
     *
     * <p>The acknowledgement mode is a constructor argument, so the legal and the illegal
     * wiring differ by one value and nothing else.
     */
    private static final class ApplyMenu extends AbstractMenu {

        private final AtomicReference<Map<String, String>> answers = new AtomicReference<>();
        private final AtomicReference<Boolean> failed = new AtomicReference<>();
        private final Ack ack;

        ApplyMenu(Ack ack) {
            super("apply");
            this.ack = ack;
        }

        @Override
        protected void declare(ActionTable.Builder table) {
            table.button("open", ack, this::open);
            table.modal(
                    "submit",
                    Ack.DEFER_REPLY,
                    (ctx, event) -> {
                        answers.set(ModalForm.read(event));
                        return CompletableFuture.completedFuture(null);
                    });
        }

        @Override
        public CompletableFuture<Container> render(MenuContext ctx) {
            return CompletableFuture.completedFuture(
                    MenuBuilder.create("apply")
                            .add(Row.of(ActionButton.primary("open", "Apply")))
                            .build(ctx));
        }

        private CompletableFuture<Void> open(MenuContext ctx, ButtonInteractionEvent event) {
            try {
                ModalForm form = ModalForm.create(ctx, "submit", "Apply", "42");
                form.shortField("name", "Name").placeholder("Ada Lovelace");
                form.paragraph("why", "Why").required(false);
                showModal(ctx, form.build());
                return CompletableFuture.completedFuture(null);
            } catch (RuntimeException refused) {
                // Recorded rather than thrown, so the test can tell "the form was
                // refused" from "the handler never ran".
                failed.set(true);
                return CompletableFuture.failedFuture(refused);
            }
        }
    }

    // ------------------------------------------------------------- helpers

    private static ButtonInteractionEvent click(String componentId) {
        return JdaMocks.button(componentId, false, MESSAGE, JdaMocks.NO_OWNER);
    }

    private static Modal captured(ButtonInteractionEvent event) {
        ArgumentCaptor<Modal> captor = ArgumentCaptor.forClass(Modal.class);
        verify(event, timeout(AWAIT_MS)).replyModal(captor.capture());
        return captor.getValue();
    }

    private static ModalInteractionEvent submission(String name, String why) {
        // The mappings are built before the stubbing starts: Mockito cannot record a stub
        // while another one is being written.
        List<ModalMapping> values = List.of(mapping("name", name), mapping("why", why));
        ModalInteractionEvent event =
                JdaMocks.modal("menu:apply:submit:42", true, MESSAGE, JdaMocks.NO_OWNER);
        when(event.getValues()).thenReturn(values);
        return event;
    }

    private static ModalMapping mapping(String id, String value) {
        ModalMapping mapping = mock(ModalMapping.class);
        when(mapping.getCustomId()).thenReturn(id);
        when(mapping.getAsString()).thenReturn(value);
        return mapping;
    }

    private static void awaitIdle(MenuRouter router) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_MS);
        while (router.inFlight() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(router.inFlight()).as("no interaction is still claimed").isZero();
    }
}
