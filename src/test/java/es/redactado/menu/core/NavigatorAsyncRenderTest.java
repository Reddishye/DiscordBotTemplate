package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavEntry;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.api.Render;
import es.redactado.menu.preset.BuiltinPresets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Navigation where the target renders asynchronously. */
class NavigatorAsyncRenderTest {

    private static final long MESSAGE = 700L;
    private static final long CLICKER = 42L;

    private SessionStore sessions;

    @BeforeEach
    void setUp() {
        sessions = new SessionStore(SessionConfig.defaults());
    }

    @AfterEach
    void tearDown() {
        sessions.close();
    }

    private Navigator navigator(Menu target) {
        return new Navigator(id -> target, sessions, Messages.standard(), TestRouters.resolver());
    }

    private static Menu menuRendering(CompletableFuture<Container> pending) {
        Menu menu = mock(Menu.class);
        when(menu.id()).thenReturn("b");
        when(menu.home(any())).thenReturn(new NavEntry("b", "home", List.of()));
        // A navigation that pushes asks the menu it is leaving which view is on screen,
        // and a mocked menu answers null for a record.
        when(menu.currentView(any()))
                .thenAnswer(
                        invocation -> {
                            MenuContext ctx = invocation.getArgument(0);
                            return new NavEntry(ctx.menuId(), ctx.action(), ctx.params());
                        });
        when(menu.render(any())).thenReturn(pending);
        return menu;
    }

    private MenuContext context(ButtonInteractionEvent event) {
        return BaseContext.fromButton(
                event,
                new ComponentId("a", "nav", List.of("push", "b")),
                sessions,
                navigator(null),
                Messages.standard(),
                BuiltinPresets.DEFAULT);
    }

    @Test
    @DisplayName("the edit happens only after an async render completes")
    void editWaitsForRender() throws InterruptedException {
        CompletableFuture<Container> pending = new CompletableFuture<>();
        Menu target = menuRendering(pending);
        ButtonInteractionEvent event = JdaMocks.button("menu:a:nav:push:b", true, MESSAGE, CLICKER);

        CompletableFuture<Void> result =
                navigator(target).go(context(event), NavigationMode.PUSH, "b");

        // Render is still in flight, so nothing has been sent.
        assertThat(event.getHook())
                .satisfies(
                        hook ->
                                verify(hook, never())
                                        .editOriginalComponents(
                                                any(MessageTopLevelComponent[].class)));

        Container container = Container.of(TextDisplay.of("late"));
        pending.complete(container);
        result.join();

        verify(event.getHook(), timeout(5_000))
                .editOriginalComponents(any(MessageTopLevelComponent[].class));
    }

    @Test
    @DisplayName("a render that completes on another thread still edits once")
    void editOnAnotherThread() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Container> pending =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                release.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return Container.of(TextDisplay.of("cross-thread"));
                        });
        Menu target = menuRendering(pending);
        ButtonInteractionEvent event = JdaMocks.button("menu:a:nav:push:b", true, MESSAGE, CLICKER);

        CompletableFuture<Void> result =
                navigator(target).go(context(event), NavigationMode.PUSH, "b");
        release.countDown();
        result.join();

        verify(event.getHook(), timeout(5_000))
                .editOriginalComponents(any(MessageTopLevelComponent[].class));
    }

    @Test
    @DisplayName("a failed render propagates and sends nothing")
    void failedRenderSendsNothing() {
        CompletableFuture<Container> pending =
                CompletableFuture.failedFuture(new IllegalStateException("load failed"));
        Menu target = menuRendering(pending);
        ButtonInteractionEvent event = JdaMocks.button("menu:a:nav:push:b", true, MESSAGE, CLICKER);

        CompletableFuture<Void> result =
                navigator(target).go(context(event), NavigationMode.PUSH, "b");

        assertThat(result).isCompletedExceptionally();
        assertThatThrownByJoin(result);
        verify(event.getHook(), never())
                .editOriginalComponents(any(MessageTopLevelComponent[].class));
    }

    @Test
    @DisplayName("a failed render is reported through the router error path")
    void failedRenderReachesRouter() {
        CompletableFuture<Container> pending =
                CompletableFuture.failedFuture(
                        new es.redactado.menu.api.UserFacingException("Nothing to show."));
        Menu target = menuRendering(pending);
        ButtonInteractionEvent event = JdaMocks.button("menu:a:nav:push:b", true, MESSAGE, CLICKER);

        MenuRouter router = TestRouters.create();
        try {
            router.register(
                    "a",
                    new AbstractMenu("a") {
                        @Override
                        protected void declare(es.redactado.menu.api.ActionTable.Builder table) {}

                        @Override
                        public CompletableFuture<Container> render(MenuContext ctx) {
                            return Render.now(Container.of(TextDisplay.of("a")));
                        }
                    });
            router.register("b", target);

            assertThat(router.dispatchButton(event)).isTrue();

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(event.getHook(), timeout(5_000)).sendMessage(captor.capture());
            assertThat(captor.getValue()).isEqualTo("Nothing to show.");
            verify(event.getHook(), never())
                    .editOriginalComponents(any(MessageTopLevelComponent[].class));
        } finally {
            router.close();
        }
    }

    private static void assertThatThrownByJoin(CompletableFuture<Void> result) {
        try {
            result.join();
            throw new AssertionError("expected the navigation to fail");
        } catch (java.util.concurrent.CompletionException expected) {
            assertThat(expected).hasRootCauseMessage("load failed");
        }
    }

    @Test
    @DisplayName("the session records nothing while the view is still rendering")
    void historyIsNotRecordedBeforeTheEditLands() {
        CompletableFuture<Container> pending = new CompletableFuture<>();
        Menu target = menuRendering(pending);
        ButtonInteractionEvent event = JdaMocks.button("menu:a:nav:push:b", true, MESSAGE, CLICKER);

        navigator(target).go(context(event), NavigationMode.PUSH, "b");

        // Deliberate: a view that fails to render must not leave a stack entry for a
        // screen the user never saw. The cost is that a Back pressed while a slow view is
        // still on its way finds an empty stack and lands on home.
        assertThat(sessions.getOrCreate(MESSAGE).depth()).isZero();

        pending.complete(Container.of(TextDisplay.of("b")));

        assertThat(sessions.getOrCreate(MESSAGE).depth()).isEqualTo(1);
    }

    @Test
    @DisplayName("back pops the stack even if the previous render is still pending")
    void backWhilePending() {
        CompletableFuture<Container> pending = new CompletableFuture<>();
        Menu target = menuRendering(pending);
        ButtonInteractionEvent pushEvent =
                JdaMocks.button("menu:a:nav:push:b", true, MESSAGE, CLICKER);
        navigator(target).go(context(pushEvent), NavigationMode.PUSH, "b");

        ButtonInteractionEvent backEvent =
                JdaMocks.button("menu:b:nav:back", true, MESSAGE, CLICKER);
        MenuContext backContext =
                BaseContext.fromButton(
                        backEvent,
                        new ComponentId("b", "nav", List.of("back")),
                        sessions,
                        navigator(target),
                        Messages.standard(),
                        BuiltinPresets.DEFAULT);

        navigator(target).go(backContext, NavigationMode.BACK, "");

        assertThat(sessions.getOrCreate(MESSAGE).depth()).isZero();
    }

    @Test
    @DisplayName("an unknown menu is reported before any render starts")
    void unknownMenu() {
        Navigator broken =
                new Navigator(
                        id -> {
                            throw new es.redactado.menu.api.MenuNotFoundException(id);
                        },
                        sessions,
                        Messages.standard(),
                        TestRouters.resolver());
        ButtonInteractionEvent event =
                JdaMocks.button("menu:a:nav:push:absent", true, MESSAGE, CLICKER);
        MenuContext ctx =
                BaseContext.fromButton(
                        event,
                        new ComponentId("a", "nav", List.of("push", "absent")),
                        sessions,
                        broken,
                        Messages.standard(),
                        BuiltinPresets.DEFAULT);

        assertThatThrownBy(() -> broken.go(ctx, NavigationMode.PUSH, "absent"))
                .isInstanceOf(es.redactado.menu.api.UserFacingException.class)
                .hasMessage(MessageKeys.ERROR_UNKNOWN_MENU);
        verify(event.getHook(), never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("Render.now wraps a container in a completed future")
    void renderNow() {
        Container container = Container.of(TextDisplay.of("x"));

        assertThat(Render.now(container).join()).isSameAs(container);
    }
}
