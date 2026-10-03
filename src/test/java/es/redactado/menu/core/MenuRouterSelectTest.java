package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.SelectHandler;
import es.redactado.menu.api.Session;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.preset.InMemoryPresetPreferences;
import es.redactado.menu.preset.Preset;
import es.redactado.menu.preset.PresetRegistry;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The select equivalents of the button dispatch tests.
 *
 * <p>Commit 1 of T10b made the three interaction kinds share one pipeline, so the risk is
 * not that a select behaves oddly but that the shared code behaves differently for a
 * select than the button tests established for a button. These tests therefore mirror
 * {@code MenuRouterDispatchTest} case for case instead of testing selects on their own
 * terms.
 */
class MenuRouterSelectTest {

    private static final int AWAIT_MS = 5_000;
    private static final long CLICKER = 42L;
    private static final long OTHER = 7L;
    private static final long MESSAGE = 100L;

    @Nested
    @DisplayName("acknowledgement")
    class Acknowledgement {

        @Test
        @DisplayName("DEFER_EDIT defers an edit")
        void deferEditAcksEdit() {
            StringSelectInteractionEvent event = JdaMocks.select("menu:m:pick", false);

            dispatch(event, Ack.DEFER_EDIT, (ctx, e) -> CompletableFuture.completedFuture(null));

            verify(event, timeout(AWAIT_MS)).deferEdit();
        }

        @Test
        @DisplayName("DEFER_REPLY defers an ephemeral reply")
        void deferReplyAcksReply() {
            StringSelectInteractionEvent event = JdaMocks.select("menu:m:pick", false);

            dispatch(event, Ack.DEFER_REPLY, (ctx, e) -> CompletableFuture.completedFuture(null));

            verify(event, timeout(AWAIT_MS)).deferReply(true);
            verify(event, never()).deferEdit();
        }

        @Test
        @DisplayName("NONE touches neither, so the action must acknowledge it itself")
        void noneAcksNothing() {
            StringSelectInteractionEvent event = JdaMocks.select("menu:m:pick", false);

            dispatch(event, Ack.NONE, (ctx, e) -> CompletableFuture.completedFuture(null));

            verify(event, never()).deferEdit();
            verify(event, never()).deferReply(true);
        }
    }

    @Test
    @DisplayName("an unknown select action is answered and consumed")
    void unknownActionIsAnswered() {
        StringSelectInteractionEvent event = JdaMocks.select("menu:m:absent", true);

        try (MenuRouter router = TestRouters.with(selectMenu(Ack.NONE, (ctx, e) -> null))) {
            assertThat(router.dispatchSelect(event)).isTrue();
        }

        assertThat(replyText(event)).isEqualTo(english(MessageKeys.ERROR_UNKNOWN_ACTION));
    }

    @Test
    @DisplayName("an unregistered menu id is left for another listener")
    void unregisteredMenuIsNotConsumed() {
        StringSelectInteractionEvent event = JdaMocks.select("menu:nothing:pick", false);

        try (MenuRouter router = TestRouters.create()) {
            assertThat(router.dispatchSelect(event)).isFalse();
        }

        verify(event, never()).reply(anyString());
    }

    @Test
    @DisplayName("a foreign submission on a personal menu is refused")
    void foreignOwnerIsRefused() {
        StringSelectInteractionEvent event =
                JdaMocks.select("menu:m:pick", true, MESSAGE, OTHER, "a");

        try (MenuRouter router = TestRouters.with(selectMenu(Ack.NONE, (ctx, e) -> null))) {
            assertThat(router.dispatchSelect(event)).isTrue();
        }

        assertThat(replyText(event)).isEqualTo(english(MessageKeys.ERROR_NOT_OWNER));
    }

    @Test
    @DisplayName("a second submission on the same message is swallowed while the first runs")
    void duplicateIsGuarded() throws InterruptedException {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        StringSelectInteractionEvent first =
                JdaMocks.select("menu:m:pick", true, MESSAGE, CLICKER, "a");
        StringSelectInteractionEvent second =
                JdaMocks.select("menu:m:pick", true, MESSAGE, CLICKER, "b");

        try (MenuRouter router =
                TestRouters.with(
                        selectMenu(
                                Ack.NONE,
                                (ctx, e) -> {
                                    entered.countDown();
                                    return new CompletableFuture<Void>()
                                            .whenComplete((v, t) -> holding.countDown());
                                }))) {
            assertThat(router.dispatchSelect(first)).isTrue();
            assertThat(entered.await(AWAIT_MS, TimeUnit.SECONDS)).isTrue();

            assertThat(router.dispatchSelect(second)).isTrue();

            // Swallowed rather than answered: the first submission will produce the
            // visible result, and a second error would contradict it.
            verify(second, timeout(AWAIT_MS)).deferReply(true);
            verify(second.getHook(), never()).sendMessage(anyString());
            holding.countDown();
        }
    }

    @Test
    @DisplayName("a failing handler produces the generic message and leaks nothing")
    void handlerFailureIsGeneric() {
        StringSelectInteractionEvent event =
                JdaMocks.select("menu:m:pick", true, MESSAGE, CLICKER, "a");

        try (MenuRouter router =
                TestRouters.with(
                        selectMenu(
                                Ack.NONE,
                                (ctx, e) ->
                                        CompletableFuture.failedFuture(
                                                new IllegalStateException("secret"))))) {
            assertThat(router.dispatchSelect(event)).isTrue();
        }

        String text = replyText(event);
        assertThat(text).matches("Something went wrong \\(ref: [0-9a-f]{6}\\)\\.");
        assertThat(text).doesNotContain("secret");
    }

    @Test
    @DisplayName("a user-facing failure is translated with the event's locale")
    void userFacingFailureIsTranslated() {
        StringSelectInteractionEvent event =
                JdaMocks.select("menu:m:pick", true, MESSAGE, CLICKER, "a");

        try (MenuRouter router =
                TestRouters.with(
                        selectMenu(
                                Ack.NONE,
                                (ctx, e) ->
                                        CompletableFuture.failedFuture(
                                                new es.redactado.menu.api.UserFacingException(
                                                        MessageKeys.ERROR_BAD_PARAM))))) {
            assertThat(router.dispatchSelect(event)).isTrue();
        }

        assertThat(replyText(event)).isEqualTo(english(MessageKeys.ERROR_BAD_PARAM));
    }

    @Test
    @DisplayName("the handler receives the chosen values and the resolved preset")
    void handlerReceivesValuesAndPreset() {
        StringSelectInteractionEvent event =
                JdaMocks.select("menu:m:pick", true, MESSAGE, CLICKER, "alpha", "beta");
        AtomicReference<List<String>> seen = new AtomicReference<>();
        AtomicReference<Preset> preset = new AtomicReference<>();

        try (MenuRouter router =
                MenuRouter.builder()
                        .presets(
                                new PresetResolver(
                                        new PresetRegistry(),
                                        new InMemoryPresetPreferences(),
                                        false))
                        .build()) {
            router.register(
                    "m",
                    selectMenu(
                            Ack.NONE,
                            (ctx, e) -> {
                                seen.set(e.getValues());
                                preset.set(ctx.preset());
                                return CompletableFuture.completedFuture(null);
                            }));

            assertThat(router.dispatchSelect(event)).isTrue();
        }

        assertThat(seen.get()).containsExactly("alpha", "beta");
        assertThat(preset.get()).isEqualTo(BuiltinPresets.DEFAULT);
    }

    @Test
    @DisplayName("the claim is released once the handler finishes")
    void claimIsReleased() {
        StringSelectInteractionEvent event =
                JdaMocks.select("menu:m:pick", true, MESSAGE, CLICKER, "a");

        try (MenuRouter router =
                TestRouters.with(
                        selectMenu(
                                Ack.NONE, (ctx, e) -> CompletableFuture.completedFuture(null)))) {
            assertThat(router.dispatchSelect(event)).isTrue();

            awaitIdle(router);
            assertThat(router.inFlight())
                    .as("a completed handler must not leave the message claimed")
                    .isZero();
        }
    }

    @Test
    @DisplayName("the handler gets the session for its message, so state survives")
    void handlerGetsItsSession() {
        StringSelectInteractionEvent first =
                JdaMocks.select("menu:m:pick", true, MESSAGE, CLICKER, "a");
        StringSelectInteractionEvent second =
                JdaMocks.select("menu:m:pick", true, MESSAGE, CLICKER, "b");
        AtomicReference<String> seenByTheSecond = new AtomicReference<>();

        // A select context with no session of its own would hand every submission a
        // fresh, empty one, so the second would read nothing. That is the regression.
        try (MenuRouter router =
                TestRouters.with(
                        selectMenu(
                                Ack.NONE,
                                (ctx, e) -> {
                                    Session session = ctx.session();
                                    seenByTheSecond.set(
                                            session.state("picks", String.class).orElse(""));
                                    session.putState("picks", e.getValues().getFirst());
                                    return CompletableFuture.completedFuture(null);
                                }))) {
            assertThat(router.dispatchSelect(first)).isTrue();
            awaitIdle(router);
            assertThat(router.dispatchSelect(second)).isTrue();
            awaitIdle(router);
        }

        assertThat(seenByTheSecond.get())
                .as("a later submission reads what the first one stored")
                .isEqualTo("a");
    }

    // ------------------------------------------------------------- helpers

    private static void dispatch(
            StringSelectInteractionEvent event, Ack ack, SelectHandler handler) {
        try (MenuRouter router = TestRouters.with(selectMenu(ack, handler))) {
            assertThat(router.dispatchSelect(event)).isTrue();
        }
    }

    private static String replyText(StringSelectInteractionEvent event) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event.getHook(), timeout(AWAIT_MS)).sendMessage(captor.capture());
        return captor.getValue();
    }

    private static void awaitIdle(MenuRouter router) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_MS);
        while (router.inFlight() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    private static Menu selectMenu(Ack ack, SelectHandler handler) {
        return new Menu() {
            @Override
            public String id() {
                return "m";
            }

            @Override
            public CompletableFuture<Container> render(MenuContext ctx) {
                throw new UnsupportedOperationException("render is not used by these tests");
            }

            @Override
            public void actions(ActionTable.Builder table) {
                table.select("pick", ack, handler);
            }
        };
    }

    private static String english(String key) {
        return Messages.standard().get(Locale.ENGLISH, key);
    }
}
