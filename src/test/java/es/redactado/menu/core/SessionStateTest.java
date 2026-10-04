package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Session;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.view.MenuBuilder;
import es.redactado.menu.view.Row;
import es.redactado.menu.view.Text;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Reading and writing session state without thinking about whether a session exists.
 *
 * <p>The bug these four methods exist to remove was real and reached production-shaped code:
 * a handler read the session with {@code findSession}, found nothing because this was the
 * first press on the message, and either failed or had to remember to create the session
 * before writing. Both examples shipped that shape until these tests caught it.
 *
 * <p>So the rules under test are: a read never creates a session, a write always ends with
 * one, and the first press on a fresh message behaves exactly like every press after it.
 */
class SessionStateTest {

    private static final String KEY = "count";
    private static final long MESSAGE = 900L;
    private static final long USER = 42L;

    @Test
    @DisplayName("a read on a fresh message answers the fallback and creates no session")
    void readDoesNotCreateASession() throws Exception {
        AtomicBoolean hadSessionBefore = new AtomicBoolean(true);
        AtomicBoolean hadSessionAfter = new AtomicBoolean(true);
        AtomicInteger read = new AtomicInteger();
        CountDownLatch ran = new CountDownLatch(1);
        Menu menu =
                stateMenu(
                        (ctx, refresh) -> {
                            hadSessionBefore.set(ctx.findSession().isPresent());
                            read.set(ctx.sessionStateOr(KEY, Integer.class, 41) + 1);
                            hadSessionAfter.set(ctx.findSession().isPresent());
                            ran.countDown();
                            return done();
                        });

        try (MenuRouter router = TestRouters.with(menu)) {
            ButtonInteractionEvent event = press(menu);

            assertThat(router.dispatchButton(event)).isTrue();

            assertThat(ran.await(5, TimeUnit.SECONDS)).as("the handler ran").isTrue();
            assertThat(hadSessionBefore)
                    .as("nothing has touched this message yet, so there is no session")
                    .isFalse();
            assertThat(hadSessionAfter)
                    .as(
                            "reading is not a reason to create one; a view with a pager would"
                                    + " otherwise leave a session behind for every message shown")
                    .isFalse();
            assertThat(read.get()).as("the fallback was used").isEqualTo(42);
        }
    }

    @Test
    @DisplayName("the first press writes into a session that the next press reads back")
    void firstPressCreatesWhatTheSecondReads() throws Exception {
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch second = new CountDownLatch(1);
        AtomicInteger seen = new AtomicInteger();
        Menu menu =
                stateMenu(
                        (ctx, refresh) -> {
                            if (seen.getAndIncrement() == 0) {
                                first.countDown();
                            } else {
                                second.countDown();
                            }
                            int current = ctx.sessionStateOr(KEY, Integer.class, 0);
                            ctx.putSessionState(KEY, current + 1);
                            return refresh.get();
                        });

        try (MenuRouter router = TestRouters.with(menu)) {
            ButtonInteractionEvent one = press(menu);
            assertThat(router.dispatchButton(one)).isTrue();
            assertThat(first.await(5, TimeUnit.SECONDS)).isTrue();
            editedText(one.getHook());

            // The same message, the way a second press arrives.
            ButtonInteractionEvent two = JdaMocks.button(idOf(menu), true, MESSAGE, USER);
            assertThat(router.dispatchButton(two)).isTrue();
            assertThat(second.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(editedText(two.getHook()))
                    .as("the first press wrote 1, so the second reads 1 and writes 2")
                    .contains("Count: 2");
        }
    }

    @Test
    @DisplayName("state of the wrong type reads as absent rather than as a cast error")
    void wrongTypeReadsAsAbsent() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        AtomicReference<Object> asString = new AtomicReference<>("unset");
        AtomicReference<Object> fallback = new AtomicReference<>();
        Menu menu =
                stateMenu(
                        (ctx, refresh) -> {
                            ctx.putSessionState(KEY, "not a number");
                            asString.set(ctx.sessionState(KEY, String.class).orElse("unset"));
                            fallback.set(ctx.sessionStateOr(KEY, Integer.class, -1));
                            ran.countDown();
                            return done();
                        });

        try (MenuRouter router = TestRouters.with(menu)) {
            assertThat(router.dispatchButton(press(menu))).isTrue();

            assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(asString.get())
                    .as("the value is a string and reads as one")
                    .isEqualTo("not a number");
            assertThat(fallback.get())
                    .as("asking for the wrong type is a missing value, not a ClassCastException")
                    .isEqualTo(-1);
        }
    }

    @Test
    @DisplayName("removing state takes it away, and removing what is not there is harmless")
    void removeStateWorks() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        AtomicReference<Integer> afterRemove = new AtomicReference<>();
        Menu menu =
                stateMenu(
                        (ctx, refresh) -> {
                            ctx.putSessionState(KEY, 7);
                            ctx.removeSessionState(KEY);
                            ctx.removeSessionState(KEY);
                            afterRemove.set(ctx.sessionStateOr(KEY, Integer.class, -1));
                            ran.countDown();
                            return done();
                        });

        try (MenuRouter router = TestRouters.with(menu)) {
            assertThat(router.dispatchButton(press(menu))).isTrue();

            assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(afterRemove.get()).isEqualTo(-1);
        }
    }

    @Test
    @DisplayName("a null key or value is refused at the call, as the session always has")
    void nullsAreRefused() {
        Session session = new Session();

        assertThat(catchThrowable(() -> session.putState(null, 1)))
                .as("the same rules as writing through the context, not a new set of them")
                .isInstanceOf(NullPointerException.class);
        assertThat(catchThrowable(() -> session.putState(KEY, null)))
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------ helpers

    private static Throwable catchThrowable(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Throwable thrown) {
            return thrown;
        }
    }

    /**
     * What the button under test does.
     *
     * <p>Takes the framework's own refresh as a supplier rather than exposing it, so a test
     * that only reads and writes does not cause an edit it never asked for.
     */
    private interface Work {
        CompletableFuture<Void> run(
                MenuContext ctx, java.util.function.Supplier<CompletableFuture<Void>> refresh);
    }

    private static CompletableFuture<Void> done() {
        return CompletableFuture.completedFuture(null);
    }

    /** One menu with one button whose handler is the code under test. */
    private static Menu stateMenu(Work work) {
        return new StateMenu(work);
    }

    private static ButtonInteractionEvent press(Menu menu) {
        return JdaMocks.button(idOf(menu), true, MESSAGE, USER);
    }

    /** The id of the menu's only button, taken from what the menu really renders. */
    private static String idOf(Menu menu) {
        MenuContext ctx = mock(MenuContext.class);
        when(ctx.menuId()).thenReturn("state");
        when(ctx.action()).thenReturn("home");
        when(ctx.params()).thenReturn(List.of());
        when(ctx.preset()).thenReturn(BuiltinPresets.DEFAULT);
        when(ctx.locale()).thenReturn(Locale.ENGLISH);
        when(ctx.t(anyString(), any(Object[].class)))
                .thenAnswer(call -> (String) call.getArgument(0));

        for (Object child : menu.render(ctx).join().getComponents()) {
            if (child instanceof ActionRow row) {
                for (Object item : row.getComponents()) {
                    if (item instanceof Button button) {
                        return button.getCustomId();
                    }
                }
            }
        }
        throw new AssertionError("the menu rendered no button");
    }

    /** The container the hook was asked to write, as text. */
    private static String editedText(InteractionHook hook) {
        ArgumentCaptor<MessageTopLevelComponent[]> captor =
                ArgumentCaptor.forClass(MessageTopLevelComponent[].class);
        verify(hook, timeout(5_000)).editOriginalComponents(captor.capture());

        StringBuilder out = new StringBuilder();
        for (MessageTopLevelComponent component : captor.getValue()) {
            if (component instanceof Container container) {
                for (Object child : container.getComponents()) {
                    if (child instanceof TextDisplay display) {
                        out.append(display.getContent()).append('\n');
                    }
                }
            }
        }
        return out.toString();
    }

    /** A menu whose one button runs the work, and whose text shows the count. */
    private static final class StateMenu extends AbstractMenu {

        private final Work work;

        StateMenu(Work work) {
            super("state");
            this.work = work;
        }

        @Override
        protected void declare(ActionTable.Builder table) {
            table.button(
                    "bump",
                    es.redactado.menu.api.Ack.DEFER_EDIT,
                    (ctx, event) -> work.run(ctx, () -> refresh(ctx)));
        }

        @Override
        public CompletableFuture<Container> render(MenuContext ctx) {
            MenuBuilder builder =
                    MenuBuilder.create("state")
                            .add(Text.of("Count: " + ctx.sessionStateOr(KEY, Integer.class, 0)));
            builder.add(Row.of(es.redactado.menu.view.ActionButton.primary("bump", "Bump")));
            return CompletableFuture.completedFuture(builder.build(ctx));
        }
    }
}
