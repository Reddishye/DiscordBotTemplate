package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavEntry;
import es.redactado.menu.api.NavigationMode;
import java.util.List;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Drives a real navigation button id through the router, which is the path a user
 * actually takes: a button built with {@link NavigationAction#buttonId}, clicked,
 * dispatched, and rendered by the target menu.
 */
class MenuRouterNavigationTest {

    private static final long MESSAGE = 900L;
    private static final long CLICKER = 42L;

    @Test
    @DisplayName("a nav button reaches the navigator and edits the message exactly once")
    void navButtonReachesNavigator() {
        Menu target = stubMenu("b");

        MenuRouter router =
                new MenuRouter(MenuExecutor.virtual(), new SessionStore(SessionConfig.defaults()));
        try {
            router.register(
                    "a",
                    new AbstractMenu("a") {
                        @Override
                        protected void declare(ActionTable.Builder table) {
                            table.button(
                                            "open",
                                            Ack.DEFER_EDIT,
                                            (ctx, event) -> ctx.navigate(NavigationMode.PUSH, "b"))
                                    .button(
                                            "home",
                                            Ack.DEFER_EDIT,
                                            (ctx, event) -> ctx.navigate(NavigationMode.BACK, ""));
                        }

                        @Override
                        protected Container build(MenuContext ctx) {
                            return Container.of(TextDisplay.of("a:" + ctx.action()));
                        }
                    });
            router.register("b", target);

            ButtonInteractionEvent event =
                    JdaMocks.button(
                            NavigationAction.buttonId("a", NavigationMode.PUSH, "b"),
                            false,
                            MESSAGE,
                            CLICKER);

            assertThat(router.dispatchButton(event)).isTrue();

            verify(target, timeout(5_000)).render(any());
            verify(event.getHook(), timeout(5_000))
                    .editOriginalComponents(any(MessageTopLevelComponent[].class));
            verify(event.getHook(), times(1))
                    .editOriginalComponents(any(MessageTopLevelComponent[].class));
        } finally {
            router.close();
        }
    }

    @Test
    @DisplayName("the session records where the back button should return to")
    void sessionRecordsHistory() {
        Menu target = stubMenu("b");
        SessionStore sessions = new SessionStore(SessionConfig.defaults());

        MenuRouter router = new MenuRouter(MenuExecutor.virtual(), sessions);
        try {
            router.register(
                    "a",
                    new AbstractMenu("a") {
                        @Override
                        protected void declare(ActionTable.Builder table) {
                            table.button(
                                    "open",
                                    Ack.DEFER_EDIT,
                                    (ctx, event) -> ctx.navigate(NavigationMode.PUSH, "b"));
                        }

                        @Override
                        protected Container build(MenuContext ctx) {
                            return Container.of(TextDisplay.of("a"));
                        }
                    });
            router.register("b", target);

            ButtonInteractionEvent event =
                    JdaMocks.button(
                            NavigationAction.buttonId("a", NavigationMode.PUSH, "b"),
                            false,
                            MESSAGE,
                            CLICKER);
            router.dispatchButton(event);

            verify(target, timeout(5_000)).render(any());

            assertThat(sessions.getOrCreate(MESSAGE).depth()).isEqualTo(1);
            assertThat(sessions.getOrCreate(MESSAGE).peek())
                    .contains(new NavEntry("a", "nav", List.of("push", "b")));
        } finally {
            router.close();
        }
    }

    private static Menu stubMenu(String id) {
        Menu menu = mock(Menu.class);
        when(menu.id()).thenReturn(id);
        when(menu.home(any())).thenReturn(new NavEntry(id, "home", List.of()));
        when(menu.render(any()))
                .thenAnswer(
                        invocation -> {
                            MenuContext ctx = invocation.getArgument(0);
                            return Container.of(TextDisplay.of(ctx.menuId() + ":" + ctx.action()));
                        });
        return menu;
    }
}
