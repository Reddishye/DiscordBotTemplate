package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavEntry;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.api.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Navigation to a named view, driven through the router as a user would.
 *
 * <p>Every click is a separate event and a separate context, which is the point: the view
 * has to come back out of the session, not out of anything the previous click left behind.
 */
class ViewNavigationEndToEndTest {

    private static final String VIEW = "view";

    private static final int AWAIT_MS = 5_000;
    private static final long MESSAGE = 4_200L;
    private static final long CLICKER = 42L;

    private final List<String> rendered = new ArrayList<>();

    @Test
    @DisplayName("pushing to a view of the same menu and going back restores the previous one")
    void pushThenBackRestoresTheView() {
        Views source = new Views("m");
        SessionStore sessions = new SessionStore(SessionConfig.defaults());

        try (MenuRouter router = router(sessions, source)) {
            // From a parameterised view, so the params have to survive too.
            assertThat(router.dispatchButton(click(push(source, "detail", "7")))).isTrue();
            awaitIdle(router);
            assertThat(rendered).containsExactly("m:detail:7");
            assertThat(depthOf(sessions)).as("one entry on the stack").isEqualTo(1);

            assertThat(router.dispatchButton(click("menu:m:nav:back"))).isTrue();
            awaitIdle(router);
            assertThat(rendered)
                    .as("back rebuilds the view it was pushed from, action and params intact")
                    .containsExactly("m:detail:7", "m:home:");
            assertThat(depthOf(sessions)).as("the entry was consumed").isZero();
        }
        sessions.close();
    }

    @Test
    @DisplayName("two pushes and two backs walk the views in order")
    void twoPushesTwoBacks() {
        Views source = new Views("m");
        SessionStore sessions = new SessionStore(SessionConfig.defaults());

        try (MenuRouter router = router(sessions, source)) {
            router.dispatchButton(click(push(source, "detail", "7")));
            awaitIdle(router);
            router.dispatchButton(click(push(source, "other")));
            awaitIdle(router);
            assertThat(depthOf(sessions)).isEqualTo(2);

            router.dispatchButton(click("menu:m:nav:back"));
            awaitIdle(router);
            assertThat(rendered.getLast()).isEqualTo("m:detail:7");

            router.dispatchButton(click("menu:m:nav:back"));
            awaitIdle(router);
            assertThat(rendered.getLast()).isEqualTo("m:home:");
            assertThat(depthOf(sessions)).isZero();
        }
        sessions.close();
    }

    @Test
    @DisplayName("a view of another menu is reachable, and going back returns home")
    void crossMenuViewTarget() {
        Views source = new Views("m");
        Views target = new Views("other");
        SessionStore sessions = new SessionStore(SessionConfig.defaults());

        try (MenuRouter router = router(sessions, source, target)) {
            String id =
                    NavigationAction.buttonId(
                            "m",
                            NavigationMode.PUSH,
                            new NavEntry("other", "detail", List.of("9")));

            assertThat(router.dispatchButton(click(id))).isTrue();
            awaitIdle(router);
            assertThat(rendered).containsExactly("other:detail:9");

            assertThat(router.dispatchButton(click("menu:other:nav:back"))).isTrue();
            awaitIdle(router);
            assertThat(rendered.getLast()).isEqualTo("m:home:");
        }
        sessions.close();
    }

    @Test
    @DisplayName("replace shows the target and leaves the stack alone")
    void replaceLeavesTheStack() {
        Views source = new Views("m");
        SessionStore sessions = new SessionStore(SessionConfig.defaults());

        try (MenuRouter router = router(sessions, source)) {
            router.dispatchButton(click(push(source, "detail", "1")));
            awaitIdle(router);

            String id =
                    NavigationAction.buttonId(
                            "m", NavigationMode.REPLACE, new NavEntry("m", "other", List.of()));
            assertThat(router.dispatchButton(click(id))).isTrue();
            awaitIdle(router);

            assertThat(rendered.getLast()).isEqualTo("m:other:");
            assertThat(depthOf(sessions)).as("replace never touches the stack").isEqualTo(1);
        }
        sessions.close();
    }

    @Test
    @DisplayName("root shows the target and clears the history")
    void rootClearsTheStack() {
        Views source = new Views("m");
        SessionStore sessions = new SessionStore(SessionConfig.defaults());

        try (MenuRouter router = router(sessions, source)) {
            router.dispatchButton(click(push(source, "detail", "1")));
            awaitIdle(router);
            assertThat(depthOf(sessions)).isEqualTo(1);

            String id =
                    NavigationAction.buttonId(
                            "m", NavigationMode.ROOT, new NavEntry("m", "other", List.of()));
            assertThat(router.dispatchButton(click(id))).isTrue();
            awaitIdle(router);

            assertThat(rendered.getLast()).isEqualTo("m:other:");
            assertThat(depthOf(sessions)).as("root forgets where the user came from").isZero();
        }
        sessions.close();
    }

    @Test
    @DisplayName("a view the menu does not know leaves the stack untouched")
    void unknownViewDoesNotTouchTheStack() {
        Views source = new Views("m");
        SessionStore sessions = new SessionStore(SessionConfig.defaults());

        try (MenuRouter router = router(sessions, source)) {
            router.dispatchButton(click(push(source, "detail", "1")));
            awaitIdle(router);
            int before = depthOf(sessions);

            String id =
                    NavigationAction.buttonId(
                            "m", NavigationMode.PUSH, new NavEntry("m", "nowhere", List.of()));
            ButtonInteractionEvent event = click(id);
            assertThat(router.dispatchButton(event)).isTrue();

            ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
            verify(event.getHook(), timeout(AWAIT_MS)).sendMessage(reply.capture());
            assertThat(reply.getValue())
                    .isEqualTo(
                            Messages.standard()
                                    .get(java.util.Locale.ENGLISH, MessageKeys.ERROR_UNKNOWN_VIEW));
            assertThat(depthOf(sessions))
                    .as("a view that was never shown must not be remembered")
                    .isEqualTo(before);
            assertThat(rendered).as("and nothing was rendered").last().isEqualTo("m:detail:1");
        }
        sessions.close();
    }

    @Test
    @DisplayName("an unknown menu is refused before anything is pushed")
    void unknownMenuIsRefused() {
        Views source = new Views("m");
        SessionStore sessions = new SessionStore(SessionConfig.defaults());

        try (MenuRouter router = router(sessions, source)) {
            String id =
                    NavigationAction.buttonId(
                            "m", NavigationMode.PUSH, new NavEntry("absent", "home", List.of()));

            assertThat(router.dispatchButton(click(id))).isTrue();
            awaitIdle(router);

            assertThat(depthOf(sessions)).isZero();
            assertThat(rendered).isEmpty();
        }
        sessions.close();
    }

    // ------------------------------------------------------------- fixtures

    private MenuRouter router(SessionStore sessions, Views... menus) {
        MenuRouter router = TestRouters.withSessions(sessions);
        for (Views menu : menus) {
            router.register(menu.id(), menu);
        }
        return router;
    }

    private String push(Views menu, String action, String... params) {
        return NavigationAction.buttonId(
                "m", NavigationMode.PUSH, new NavEntry(menu.id(), action, List.of(params)));
    }

    private static ButtonInteractionEvent click(String componentId) {
        return JdaMocks.button(componentId, true, MESSAGE, CLICKER);
    }

    private static int depthOf(SessionStore sessions) {
        return sessions.find(MESSAGE).map(Session::depth).orElse(0);
    }

    private static void awaitIdle(MenuRouter router) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_MS);
        while (router.inFlight() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(router.inFlight()).as("no interaction is still claimed").isZero();
    }

    /**
     * A menu with three views that records what each render was asked for.
     *
     * <p>An inner class so every menu writes into the one list the test asserts on, which
     * is what makes "back rendered the source, not the target" checkable at all.
     */
    private final class Views extends AbstractMenu {

        private Views(String id) {
            super(id);
        }

        @Override
        protected void declare(ActionTable.Builder table) {}

        @Override
        public CompletableFuture<Container> render(MenuContext ctx) {
            String label = ctx.menuId() + ":" + ctx.action() + ":" + String.join(",", ctx.params());
            ctx.session().putState(VIEW, new NavEntry(ctx.menuId(), ctx.action(), ctx.params()));
            return switch (ctx.action()) {
                case "home", "detail", "other" -> {
                    rendered.add(label);
                    yield CompletableFuture.completedFuture(Container.of(TextDisplay.of(label)));
                }
                // The framework's own answer for an action this menu does not handle.
                default -> unknownView(ctx);
            };
        }

        /**
         * The view it is showing, which is the last one it rendered.
         *
         * <p>This is the override a menu with several views needs. The framework default
         * answers the action of the incoming click, which after a navigation button is
         * {@code nav}, and going back would then render an action no view recognises.
         */
        @Override
        public NavEntry currentView(MenuContext ctx) {
            return ctx.findSession()
                    .flatMap(session -> session.state(VIEW, NavEntry.class))
                    .orElseGet(() -> home(ctx));
        }
    }
}
