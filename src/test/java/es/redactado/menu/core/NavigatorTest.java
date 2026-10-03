package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.MenuNotFoundException;
import es.redactado.menu.api.NavEntry;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.api.Session;
import es.redactado.menu.api.UserFacingException;
import java.util.List;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Navigation tests.
 *
 * <p>Each test uses two separate button events and two separate contexts, one per
 * click. That is the defect being fixed: in the original implementation the back
 * stack lived on the context, so it was always empty by the time the next click
 * arrived and going back could never work.
 */
class NavigatorTest {

    private static final int AWAIT_MS = 5_000;
    private static final long MESSAGE = 500L;
    private static final long CLICKER = 42L;

    private SessionStore sessions;
    private Menu menuA;
    private Menu menuB;

    /** Records which entry each render was asked for, so history order is checkable. */
    private final List<String> rendered = new java.util.ArrayList<>();

    private static Container container(String label) {
        return Container.of(TextDisplay.of(label));
    }

    @BeforeEach
    void setUp() {
        sessions = new SessionStore(SessionConfig.defaults());

        menuA = stubMenu("a");
        menuB = stubMenu("b");
    }

    /** Resolves the two stubbed menus and rejects anything else, like the router does. */
    private Menu resolve(String menuId) {
        return switch (menuId) {
            case "a" -> menuA;
            case "b" -> menuB;
            default -> throw new MenuNotFoundException(menuId);
        };
    }

    private Menu stubMenu(String id) {
        Menu menu = mock(Menu.class);
        when(menu.id()).thenReturn(id);
        when(menu.home(any())).thenReturn(new NavEntry(id, "home", List.of()));
        when(menu.render(any()))
                .thenAnswer(
                        invocation -> {
                            MenuContext ctx = invocation.getArgument(0);
                            rendered.add(ctx.menuId() + ":" + ctx.action());
                            return container(ctx.menuId() + ":" + ctx.action());
                        });
        return menu;
    }

    @AfterEach
    void tearDown() {
        sessions.close();
    }

    private Navigator navigator;

    private Navigator navigator() {
        if (navigator == null) {
            navigator = new Navigator(this::resolve, sessions);
        }
        return navigator;
    }

    private MenuContext contextFor(ButtonInteractionEvent event, String menuId, String action) {
        return BaseContext.fromButton(
                event, new ComponentId(menuId, action, List.of()), sessions, navigator());
    }

    private ButtonInteractionEvent click(String componentId) {
        return JdaMocks.button(componentId, true, MESSAGE, CLICKER);
    }

    @Nested
    @DisplayName("push and back across separate clicks")
    class PushBack {

        @Test
        @DisplayName("back after a push restores the view that was pushed")
        void backRestoresPushedView() {
            Navigator navigator = navigator();

            ButtonInteractionEvent first = click("menu:a:nav:push:b");
            navigator.go(contextFor(first, "a", "nav"), NavigationMode.PUSH, "b");

            assertThat(rendered).containsExactly("b:home");

            // A different click, a different event, a different context: this is
            // exactly the case the old per-event back stack could not serve.
            ButtonInteractionEvent second = click("menu:b:nav:back");
            navigator.go(contextFor(second, "b", "nav"), NavigationMode.BACK, "");

            assertThat(rendered).containsExactly("b:home", "a:nav");
            verify(second.getHook(), timeout(AWAIT_MS))
                    .editOriginalComponents(any(MessageTopLevelComponent[].class));
        }

        @Test
        @DisplayName("two pushes then two backs restore the views in order")
        void twoPushesTwoBacks() {
            Navigator navigator = navigator();

            navigator.go(contextFor(click("x"), "a", "one"), NavigationMode.PUSH, "b");
            navigator.go(contextFor(click("x"), "b", "two"), NavigationMode.PUSH, "a");

            assertThat(sessions.getOrCreate(MESSAGE).depth()).isEqualTo(2);

            navigator.go(contextFor(click("x"), "a", "nav"), NavigationMode.BACK, "");
            navigator.go(contextFor(click("x"), "b", "nav"), NavigationMode.BACK, "");

            // PUSH records the current view and renders only the target, so the
            // history is a:one then b:two, restored in that order.
            assertThat(rendered).containsExactly("b:home", "a:home", "b:two", "a:one");
            assertThat(sessions.getOrCreate(MESSAGE).depth()).isZero();
        }
    }

    @Nested
    @DisplayName("back edge cases")
    class BackEdges {

        @Test
        @DisplayName("an expired session warns and shows the current home view")
        void expiredSessionWarns() {
            Navigator navigator = navigator();
            ButtonInteractionEvent event = click("menu:a:nav:back");
            MenuContext ctx = contextFor(event, "a", "nav");
            assertThat(sessions.find(MESSAGE)).isEmpty();

            navigator.go(ctx, NavigationMode.BACK, "");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(event.getHook(), timeout(AWAIT_MS)).sendMessage(captor.capture());
            assertThat(captor.getValue()).isEqualTo("This menu expired.");
            assertThat(rendered).containsExactly("a:home");
        }

        @Test
        @DisplayName("an empty stack shows home silently")
        void emptyStackIsSilent() {
            Navigator navigator = navigator();
            ButtonInteractionEvent event = click("menu:a:nav:back");
            MenuContext ctx = contextFor(event, "a", "nav");
            sessions.getOrCreate(MESSAGE);

            navigator.go(ctx, NavigationMode.BACK, "");

            assertThat(rendered).containsExactly("a:home");
            verify(event.getHook(), never()).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("other modes")
    class OtherModes {

        @Test
        @DisplayName("root clears the history")
        void rootClears() {
            Navigator navigator = navigator();
            navigator.go(contextFor(click("x"), "a", "one"), NavigationMode.PUSH, "b");
            navigator.go(contextFor(click("x"), "b", "two"), NavigationMode.PUSH, "a");
            assertThat(sessions.getOrCreate(MESSAGE).depth()).isEqualTo(2);

            navigator.go(contextFor(click("x"), "a", "nav"), NavigationMode.ROOT, "a");

            assertThat(sessions.getOrCreate(MESSAGE).depth()).isZero();
            assertThat(rendered).containsExactly("b:home", "a:home", "a:home");
        }

        @Test
        @DisplayName("replace shows the target without touching the stack")
        void replaceLeavesStack() {
            Navigator navigator = navigator();
            navigator.go(contextFor(click("x"), "a", "one"), NavigationMode.PUSH, "b");
            navigator.go(contextFor(click("x"), "a", "two"), NavigationMode.REPLACE, "a");

            assertThat(sessions.getOrCreate(MESSAGE).depth()).isEqualTo(1);
            assertThat(rendered).containsExactly("b:home", "a:home");
        }

        @Test
        @DisplayName("an unknown target reports a user-facing error")
        void unknownTarget() {
            Navigator navigator = navigator();
            MenuContext ctx = contextFor(click("x"), "a", "nav");

            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> navigator.go(ctx, NavigationMode.PUSH, "absent"))
                    .isInstanceOf(UserFacingException.class)
                    .hasMessage("Unknown menu.");
        }
    }

    @Test
    @DisplayName("the session is shared across contexts for the same message")
    void sessionSharedAcrossContexts() {
        MenuContext first = contextFor(click("x"), "a", "one");
        MenuContext second = contextFor(click("x"), "a", "two");

        Session a = first.session();
        Session b = second.session();

        assertThat(b).isSameAs(a);
        a.putState("page", 1);
        assertThat(b.state("page", Integer.class)).contains(1);
    }

    @Test
    @DisplayName("at() keeps the session and the event but changes the addressed view")
    void atKeepsSessionAndEvent() {
        ButtonInteractionEvent event = click("x");
        MenuContext ctx = contextFor(event, "a", "one");

        MenuContext moved = ctx.at(new NavEntry("b", "two", List.of("p")));

        assertThat(moved.menuId()).isEqualTo("b");
        assertThat(moved.action()).isEqualTo("two");
        assertThat(moved.params()).containsExactly("p");
        assertThat(moved.event()).isSameAs(ctx.event());
        assertThat(moved.messageId()).hasValue(MESSAGE);
    }

    @Test
    @DisplayName("an interaction with no message gets a detached session")
    void noMessageGetsDetachedSession() {
        ButtonInteractionEvent event = JdaMocks.button("menu:a:home", true);
        MenuContext ctx =
                BaseContext.fromButton(
                        event, new ComponentId("a", "home", List.of()), sessions, navigator());

        assertThat(ctx.messageId()).isEmpty();
        assertThat(ctx.findSession()).isEmpty();

        ctx.session().putState("k", 1);

        assertThat(ctx.session().state("k", Integer.class)).isEmpty();
    }
}
