package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.view.Confirm;
import es.redactado.menu.view.MenuBuilder;
import es.redactado.menu.view.Nav;
import es.redactado.menu.view.Row;
import es.redactado.menu.view.Text;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * A confirmation driven through the router, as a user would.
 *
 * <p>The point is the cancel path: it must return to the previous view without running any
 * action of its own, which is only true if the button really is a back.
 */
class ConfirmEndToEndTest {

    private static final int AWAIT_MS = 5_000;
    private static final long MESSAGE = 700L;

    @Test
    @DisplayName("confirming runs the action exactly once and returns to the previous view")
    void confirmRunsOnce() {
        CountingMenu list = new CountingMenu("m");
        CountingMenu confirm = new CountingMenu("confirm");
        try (MenuRouter router = TestRouters.create()) {
            router.register("m", list);
            router.register("confirm", confirm);

            // Reach the confirmation the documented way: push it from the list view.
            ButtonInteractionEvent push =
                    JdaMocks.button("menu:m:nav:push:confirm", true, MESSAGE, JdaMocks.NO_OWNER);
            assertThat(router.dispatchButton(push)).isTrue();
            assertThat(edited(push))
                    .as("the confirmation is showing")
                    .contains("Delete this account?");

            ButtonInteractionEvent yes =
                    JdaMocks.button(
                            "menu:confirm:reallyDelete:42", true, MESSAGE, JdaMocks.NO_OWNER);
            assertThat(router.dispatchButton(yes)).isTrue();

            assertThat(edited(yes))
                    .as("back on the list, not the confirmation")
                    .contains("Profile")
                    .doesNotContain("Delete this account?");
            assertThat(confirm.deletes.get()).as("one click, one delete").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("cancelling returns to the previous view without running the action")
    void cancelReturnsWithoutActing() {
        CountingMenu list = new CountingMenu("m");
        CountingMenu confirm = new CountingMenu("confirm");
        try (MenuRouter router = TestRouters.create()) {
            router.register("m", list);
            router.register("confirm", confirm);

            ButtonInteractionEvent push =
                    JdaMocks.button("menu:m:nav:push:confirm", true, MESSAGE, JdaMocks.NO_OWNER);
            assertThat(router.dispatchButton(push)).isTrue();
            awaitIdle(router);

            ButtonInteractionEvent cancel =
                    JdaMocks.button("menu:confirm:nav:back", true, MESSAGE, JdaMocks.NO_OWNER);
            assertThat(router.dispatchButton(cancel)).isTrue();

            assertThat(edited(cancel))
                    .as("back on the list")
                    .contains("Profile")
                    .doesNotContain("Delete this account?");
            assertThat(confirm.deletes.get()).as("cancelling must not delete").isZero();
        }
    }

    /**
     * One menu, two views.
     *
     * <p>The list shows a profile summary; anything else is the confirmation. The delete
     * handler navigates back, which is what sends the user to the list again.
     */
    private static final class CountingMenu extends AbstractMenu {

        private final AtomicInteger deletes = new AtomicInteger();
        private final String id;

        CountingMenu(String id) {
            super(id);
            this.id = id;
        }

        @Override
        protected void declare(ActionTable.Builder table) {
            table.button(
                    "reallyDelete",
                    Ack.DEFER_EDIT,
                    (ctx, event) -> {
                        deletes.incrementAndGet();
                        return ctx.navigate(NavigationMode.BACK, "");
                    });
        }

        @Override
        public CompletableFuture<Container> render(MenuContext ctx) {
            Container container;
            if ("confirm".equals(id)) {
                container =
                        MenuBuilder.create(id)
                                .add(
                                        Confirm.of(
                                                Text.of("Delete this account?"),
                                                "reallyDelete",
                                                "42"))
                                .add(Row.of(Nav.push("m", "Back to the list")))
                                .build(ctx);
            } else {
                container =
                        MenuBuilder.create(id)
                                .add(Text.of("Profile"))
                                .add(Row.of(Nav.push("confirm", "Delete")))
                                .build(ctx);
            }
            return CompletableFuture.completedFuture(container);
        }
    }

    /** Waits for the router to finish every interaction it has claimed. */
    private static void awaitIdle(MenuRouter router) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_MS);
        while (router.inFlight() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(router.inFlight()).as("no interaction is still claimed").isZero();
    }

    /** The container the interaction's hook was asked to edit the message to, as text. */
    private static String edited(ButtonInteractionEvent event) {
        ArgumentCaptor<MessageTopLevelComponent[]> captor =
                ArgumentCaptor.forClass(MessageTopLevelComponent[].class);
        verify(event.getHook(), timeout(AWAIT_MS)).editOriginalComponents(captor.capture());
        StringBuilder out = new StringBuilder();
        for (MessageTopLevelComponent component : captor.getValue()) {
            if (component instanceof Container container) {
                for (var child : container.getComponents()) {
                    if (child instanceof TextDisplay display) {
                        out.append(display.getContent()).append('\n');
                    } else if (child instanceof ActionRow row) {
                        for (var item : row.getComponents()) {
                            if (item instanceof Button button) {
                                out.append(button.getLabel()).append('\n');
                            }
                        }
                    }
                }
            }
        }
        return out.toString();
    }
}
