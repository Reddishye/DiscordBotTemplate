package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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
import es.redactado.menu.api.Render;
import es.redactado.menu.api.UserFacingException;
import es.redactado.menu.preset.BuiltinPresets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Covers the owner check, the re-entrancy guard, the executor hand-off, and the error path. */
class MenuRouterDispatchTest {

    private static final int AWAIT_MS = 5_000;
    private static final long CLICKER = 42L;
    private static final long STRANGER = 99L;

    /** A menu with one button action whose handler is supplied by the test. */
    private static final class SimpleMenu implements Menu {
        private final ButtonHandler handler;
        private final boolean shared;

        SimpleMenu(ButtonHandler handler, boolean shared) {
            this.handler = handler;
            this.shared = shared;
        }

        @Override
        public String id() {
            return "m";
        }

        @Override
        public boolean shared() {
            return shared;
        }

        @Override
        public CompletableFuture<Container> render(MenuContext ctx) {
            return Render.now(Container.of(TextDisplay.of("body")));
        }

        @Override
        public void actions(ActionTable.Builder table) {
            table.button("go", Ack.DEFER_EDIT, handler);
        }
    }

    private static MenuRouter router(ButtonHandler handler, boolean shared) {
        MenuRouter router = TestRouters.create();
        router.register("m", new SimpleMenu(handler, shared));
        return router;
    }

    private static ButtonHandler instantHandler() {
        ButtonHandler handler = mock(ButtonHandler.class);
        when(handler.handle(any(), any())).thenReturn(Done.NOW);
        return handler;
    }

    private static ButtonInteractionEvent click() {
        return JdaMocks.button("menu:m:go", false, 1000L, CLICKER);
    }

    private static ButtonInteractionEvent clickFrom(long messageId, long ownerId) {
        return JdaMocks.button("menu:m:go", false, messageId, ownerId);
    }

    /** Spins until the router reports the expected number of in-flight messages. */
    private static void awaitInFlight(MenuRouter router, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && router.inFlight() != expected) {
            Thread.onSpinWait();
        }
        assertThat(router.inFlight()).isEqualTo(expected);
    }

    @Nested
    @DisplayName("owner check")
    class OwnerCheck {

        @Test
        @DisplayName("a foreign user is denied without an ack or a handler run")
        void foreignUserDenied() {
            ButtonHandler handler = instantHandler();
            try (MenuRouter router = router(handler, false)) {
                ButtonInteractionEvent event = clickFrom(1000L, STRANGER);

                assertThat(router.dispatchButton(event)).isTrue();

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(event, timeout(AWAIT_MS)).reply(captor.capture());
                assertThat(captor.getValue()).isEqualTo("This menu is not yours.");
                verify(event, never()).deferEdit();
                verify(event, never()).deferReply(true);
                verifyNoInteractions(handler);
                assertThat(router.inFlight()).isZero();
            }
        }

        @Test
        @DisplayName("the owner is allowed")
        void ownerAllowed() {
            ButtonHandler handler = instantHandler();
            try (MenuRouter router = router(handler, false)) {
                assertThat(router.dispatchButton(clickFrom(1000L, CLICKER))).isTrue();

                verify(handler, timeout(AWAIT_MS)).handle(any(), any());
            }
        }

        @Test
        @DisplayName("a message with no interaction metadata is allowed")
        void noMetadataAllowed() {
            ButtonHandler handler = instantHandler();
            try (MenuRouter router = router(handler, false)) {
                ButtonInteractionEvent event =
                        JdaMocks.button("menu:m:go", false, 1000L, JdaMocks.NO_OWNER);

                assertThat(router.dispatchButton(event)).isTrue();

                verify(handler, timeout(AWAIT_MS)).handle(any(), any());
            }
        }

        @Test
        @DisplayName("a shared menu is allowed regardless of the owner")
        void sharedAllowed() {
            ButtonHandler handler = instantHandler();
            try (MenuRouter router = router(handler, true)) {
                assertThat(router.dispatchButton(clickFrom(1000L, STRANGER))).isTrue();

                verify(handler, timeout(AWAIT_MS)).handle(any(), any());
            }
        }

        @Test
        @DisplayName("an interaction with no message skips both the check and the guard")
        void noMessageSkipsGuard() {
            ButtonHandler handler = instantHandler();
            try (MenuRouter router = router(handler, false)) {
                assertThat(router.dispatchButton(JdaMocks.button("menu:m:go", false))).isTrue();
                assertThat(router.dispatchButton(JdaMocks.button("menu:m:go", false))).isTrue();

                verify(handler, timeout(AWAIT_MS).times(2)).handle(any(), any());
            }
        }
    }

    @Nested
    @DisplayName("executor hand-off")
    class Executor {

        @Test
        @DisplayName("the ack runs on the calling thread and the handler on a virtual thread")
        void threading() throws InterruptedException {
            CountDownLatch ran = new CountDownLatch(1);
            AtomicReference<Thread> handlerThread = new AtomicReference<>();
            AtomicBoolean virtual = new AtomicBoolean();
            AtomicReference<String> name = new AtomicReference<>();
            Thread caller = Thread.currentThread();

            ButtonHandler handler =
                    (ctx, event) -> {
                        handlerThread.set(Thread.currentThread());
                        virtual.set(Thread.currentThread().isVirtual());
                        name.set(Thread.currentThread().getName());
                        ran.countDown();
                        return Done.NOW;
                    };

            try (MenuRouter router = router(handler, false)) {
                ButtonInteractionEvent event = clickFrom(1000L, CLICKER);

                assertThat(router.dispatchButton(event)).isTrue();

                // The ack happened inline, before dispatch returned.
                verify(event).deferEdit();

                assertThat(ran.await(AWAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
                assertThat(handlerThread.get()).isNotSameAs(caller);
                assertThat(virtual).isTrue();
                assertThat(name.get()).startsWith("menu-");
            }
        }

        @Test
        @DisplayName("close gives up on a stuck handler after about five seconds")
        void closeIsBounded() throws InterruptedException {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch neverReleased = new CountDownLatch(1);

            // A handler that blocks its own virtual thread. Note the executor task
            // ends as soon as the handler's future is attached, so a handler that
            // merely returns an incomplete future would NOT hold close() open; the
            // handler body itself has to be stuck.
            ButtonHandler handler =
                    (ctx, event) -> {
                        started.countDown();
                        try {
                            neverReleased.await(60, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return Done.NOW;
                    };

            MenuRouter router = router(handler, false);
            assertThat(router.dispatchButton(clickFrom(1000L, CLICKER))).isTrue();
            assertThat(started.await(AWAIT_MS, TimeUnit.MILLISECONDS)).isTrue();

            long before = System.nanoTime();
            router.close();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);

            assertThat(elapsedMillis).isBetween(3_000L, 9_000L);
            neverReleased.countDown();
        }
    }

    @Nested
    @DisplayName("re-entrancy guard")
    class Guard {

        @Test
        @DisplayName("a duplicate click is dropped and a later click is accepted")
        void duplicateDropped() throws InterruptedException {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch again = new CountDownLatch(1);
            AtomicReference<CompletableFuture<Void>> pending = new AtomicReference<>();

            ButtonHandler handler =
                    (ctx, event) -> {
                        CompletableFuture<Void> future = new CompletableFuture<>();
                        if (started.getCount() > 0) {
                            pending.set(future);
                            started.countDown();
                        } else {
                            again.countDown();
                        }
                        return future;
                    };

            try (MenuRouter router = router(handler, false)) {
                ButtonInteractionEvent first = clickFrom(7L, CLICKER);
                ButtonInteractionEvent duplicate = clickFrom(7L, CLICKER);

                assertThat(router.dispatchButton(first)).isTrue();
                assertThat(started.await(AWAIT_MS, TimeUnit.MILLISECONDS)).isTrue();

                assertThat(router.dispatchButton(duplicate)).isTrue();

                // Swallowed with a silent deferEdit, never answered to the user.
                verify(duplicate).deferEdit();
                verify(duplicate, never()).reply(anyString());
                verify(duplicate, never()).deferReply(true);

                // Releasing the first interaction frees the message again.
                pending.get().complete(null);
                awaitInFlight(router, 0);

                ButtonInteractionEvent third = clickFrom(7L, CLICKER);
                assertThat(router.dispatchButton(third)).isTrue();
                assertThat(again.await(AWAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
            }
        }

        @Test
        @DisplayName("different messages are handled in parallel")
        void differentMessagesRunInParallel() throws InterruptedException {
            CountDownLatch bothStarted = new CountDownLatch(2);
            ButtonHandler handler =
                    (ctx, event) -> {
                        bothStarted.countDown();
                        return new CompletableFuture<>();
                    };

            try (MenuRouter router = router(handler, false)) {
                assertThat(router.dispatchButton(clickFrom(1L, CLICKER))).isTrue();
                assertThat(router.dispatchButton(clickFrom(2L, CLICKER))).isTrue();

                assertThat(bothStarted.await(AWAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
                assertThat(router.inFlight()).isEqualTo(2);
            }
        }

        @Test
        @DisplayName("the claim is released after success")
        void releasedAfterSuccess() {
            try (MenuRouter router = router(instantHandler(), false)) {
                router.dispatchButton(clickFrom(1000L, CLICKER));

                awaitInFlight(router, 0);
            }
        }

        @Test
        @DisplayName("the claim is released after a synchronous throw")
        void releasedAfterThrow() {
            ButtonHandler handler = mock(ButtonHandler.class);
            when(handler.handle(any(), any())).thenThrow(new IllegalStateException("boom"));

            try (MenuRouter router = router(handler, false)) {
                router.dispatchButton(clickFrom(1000L, CLICKER));

                awaitInFlight(router, 0);
            }
        }

        @Test
        @DisplayName("the claim is released after a failed future")
        void releasedAfterFailedFuture() {
            ButtonHandler handler = mock(ButtonHandler.class);
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("boom"));
            when(handler.handle(any(), any())).thenReturn(failed);

            try (MenuRouter router = router(handler, false)) {
                router.dispatchButton(clickFrom(1000L, CLICKER));

                awaitInFlight(router, 0);
            }
        }

        @Test
        @DisplayName("the claim is released and the user told when the executor rejects")
        void releasedAfterRejection() {
            ExecutorService rejecting = mock(ExecutorService.class);
            doThrow(new RejectedExecutionException()).when(rejecting).execute(any(Runnable.class));

            MenuRouter router = TestRouters.withExecutor(MenuExecutor.of(rejecting));
            router.register("m", new SimpleMenu(instantHandler(), false));
            ButtonInteractionEvent event = clickFrom(1000L, CLICKER);

            assertThat(router.dispatchButton(event)).isTrue();

            assertThat(router.inFlight()).isZero();
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(event).reply(captor.capture());
            assertThat(captor.getValue()).isEqualTo("The bot is busy. Try again.");
        }
    }

    @Nested
    @DisplayName("context parameter helpers")
    class ContextHelpers {

        @Test
        @DisplayName("requireLong parses a valid value")
        void parsesLong() {
            assertThat(contextWith("42").requireLong(0)).isEqualTo(42L);
        }

        @Test
        @DisplayName("requireLong rejects a malformed value")
        void rejectsMalformed() {
            assertThatThrownBy(() -> contextWith("4x2").requireLong(0))
                    .isInstanceOf(UserFacingException.class)
                    .hasMessage(MessageKeys.ERROR_BAD_PARAM);
        }

        @Test
        @DisplayName("requireLong rejects a missing value")
        void rejectsMissing() {
            assertThatThrownBy(() -> contextWith(null).requireLong(0))
                    .isInstanceOf(UserFacingException.class);
        }

        @Test
        @DisplayName("requireInt parses and rejects overflow")
        void parsesInt() {
            assertThat(contextWith("7").requireInt(0)).isEqualTo(7);
            assertThatThrownBy(() -> contextWith("99999999999999").requireInt(0))
                    .isInstanceOf(UserFacingException.class);
        }

        @Test
        @DisplayName("requireString delegates and rejects blank")
        void parsesString() {
            assertThat(contextWith("hello").requireString(0)).isEqualTo("hello");
            assertThatThrownBy(() -> contextWith("   ").requireString(0))
                    .isInstanceOf(UserFacingException.class);
        }

        private MenuContext contextWith(String param) {
            List<String> params = param == null ? List.of() : List.of(param);
            SessionStore store = new SessionStore(SessionConfig.defaults());
            return BaseContext.fromButton(
                    JdaMocks.button("menu:m:go", false),
                    new ComponentId("m", "go", params),
                    store,
                    new Navigator(id -> null, store, Messages.standard(), TestRouters.resolver()),
                    Messages.standard(),
                    BuiltinPresets.DEFAULT);
        }
    }
}
