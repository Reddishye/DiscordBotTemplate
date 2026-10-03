package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.Limits;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.view.MenuBuilder;
import es.redactado.menu.view.Pager;
import es.redactado.menu.view.Text;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
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
 * Paging through the router, as a user would.
 *
 * <p>Separate from {@code PagerTest} because what is being checked here is not what a
 * pager renders but that the whole loop closes: a page button produces an id the router
 * accepts, the handler stores a new page, and the message the user is looking at is
 * edited to show it. A pager that renders correctly but whose buttons do nothing would
 * pass every other test here.
 */
class PagerEndToEndTest {

    private static final int AWAIT_MS = 5_000;
    private static final long MESSAGE = 500L;
    private static final long USER = 42L;

    @Test
    @DisplayName("next, next and previous each re-render the page the user asked for")
    void pagingThroughTheRouter() {
        PagingMenu menu = new PagingMenu(25, 5);
        try (MenuRouter router = TestRouters.with(menu)) {
            // Everything starts from an empty session, so the first click is also the
            // proof that a missing session means page one: next from nothing is page two.
            ButtonInteractionEvent first = click("menu:list:page:next:pager:home");
            assertThat(router.dispatchButton(first)).isTrue();
            assertThat(textOf(first))
                    .as("next from nothing lands on page two")
                    .contains("2/5")
                    .contains("item 5");

            ButtonInteractionEvent second = click("menu:list:page:next:pager:home");
            assertThat(router.dispatchButton(second)).isTrue();
            assertThat(textOf(second))
                    .as("the third page")
                    .contains("3/5")
                    .contains("item 10")
                    .doesNotContain("item 5");

            ButtonInteractionEvent previous = click("menu:list:page:prev:pager:home");
            assertThat(router.dispatchButton(previous)).isTrue();
            assertThat(textOf(previous))
                    .as("back to the second page")
                    .contains("2/5")
                    .contains("item 5")
                    .doesNotContain("item 10");
        }
    }

    @Test
    @DisplayName("previous from an empty session stays on the first page")
    void previousFromNothingStaysOnFirstPage() {
        PagingMenu menu = new PagingMenu(25, 5);
        try (MenuRouter router = TestRouters.with(menu)) {
            ButtonInteractionEvent previous = click("menu:list:page:prev:pager:home");

            assertThat(router.dispatchButton(previous)).isTrue();

            assertThat(textOf(previous)).contains("1/5").contains("item 0");
            assertThat(menu.lastPage().get())
                    .as("the raw -1 is stored and clamped on read")
                    .isEqualTo(-1);
        }
    }

    @Test
    @DisplayName("two clicks on the same message are refused rather than racing")
    void secondClickIsGuarded() {
        PagingMenu menu = new PagingMenu(25, 5);
        try (MenuRouter router = TestRouters.with(menu)) {
            ButtonInteractionEvent first = click("menu:list:page:next:pager:home");
            assertThat(router.dispatchButton(first)).isTrue();
            assertThat(textOf(first)).contains("2/5");

            ButtonInteractionEvent second = click("menu:list:page:next:pager:home");
            assertThat(router.dispatchButton(second)).isTrue();

            org.mockito.Mockito.verify(second, timeout(AWAIT_MS)).deferEdit();
        }
    }

    @Test
    @DisplayName("a page of the largest size still fits the container limit")
    void pageStaysWithinLimits() {
        PagingMenu menu = new PagingMenu(500, 10);

        Container container = menu.build(menu.contextStub());

        assertThat(container.getComponents().size())
                .as("ten items plus the control row")
                .isLessThanOrEqualTo(Limits.MAX_CONTAINER_CHILDREN);
    }

    // ------------------------------------------------------------- helpers

    /** A menu whose only view is a pager over a fixed list. */
    private static final class PagingMenu extends AbstractMenu {

        private final int count;
        private final int pageSize;
        private final java.util.concurrent.atomic.AtomicInteger lastPage =
                new java.util.concurrent.atomic.AtomicInteger(Integer.MIN_VALUE);

        PagingMenu(int count, int pageSize) {
            super("list");
            this.count = count;
            this.pageSize = pageSize;
        }

        java.util.concurrent.atomic.AtomicInteger lastPage() {
            return lastPage;
        }

        @Override
        protected void declare(ActionTable.Builder table) {}

        @Override
        public CompletableFuture<Container> render(MenuContext ctx) {
            lastPage.set(
                    ctx.session()
                            .state(Pager.stateKey("pager"), Integer.class)
                            .orElse(Integer.MIN_VALUE));
            return CompletableFuture.completedFuture(build(ctx));
        }

        Container build(MenuContext ctx) {
            List<String> items = IntStream.range(0, count).mapToObj(i -> "item " + i).toList();
            return MenuBuilder.create("list")
                    .add(Pager.of("pager", items, pageSize, Text::of))
                    .build(ctx);
        }

        MenuContext contextStub() {
            es.redactado.menu.api.MenuContext ctx = org.mockito.Mockito.mock(MenuContext.class);
            net.dv8tion.jda.api.entities.User user =
                    org.mockito.Mockito.mock(net.dv8tion.jda.api.entities.User.class);
            org.mockito.Mockito.when(user.getEffectiveName()).thenReturn("Ada");
            org.mockito.Mockito.when(ctx.menuId()).thenReturn("list");
            org.mockito.Mockito.when(ctx.action()).thenReturn("home");
            org.mockito.Mockito.when(ctx.params()).thenReturn(List.of());
            org.mockito.Mockito.when(ctx.preset()).thenReturn(BuiltinPresets.DEFAULT);
            org.mockito.Mockito.when(ctx.discordUser()).thenReturn(user);
            org.mockito.Mockito.when(ctx.session()).thenReturn(new es.redactado.menu.api.Session());
            return ctx;
        }
    }

    private static ButtonInteractionEvent click(String componentId) {
        ButtonInteractionEvent event =
                JdaMocks.button(componentId, true, MESSAGE, JdaMocks.NO_OWNER);
        org.mockito.Mockito.when(event.getUser().getIdLong()).thenReturn(USER);
        return event;
    }

    /**
     * The container the interaction's hook was asked to edit the message to.
     *
     * <p>Captures the varargs overload, which is the one {@code ViewEditor} calls: it
     * passes a single container, so it binds to {@code editOriginalComponents(MessageTop-
     * LevelComponent...)} rather than to the {@code Collection} overload. Capturing the
     * wrong one produces "no invocation matched" for a call that did happen.
     */
    private static Container edited(ButtonInteractionEvent event) {
        ArgumentCaptor<MessageTopLevelComponent[]> captor =
                ArgumentCaptor.forClass(MessageTopLevelComponent[].class);
        verify(event.getHook(), timeout(AWAIT_MS)).editOriginalComponents(captor.capture());
        return java.util.Arrays.stream(captor.getValue())
                .filter(Container.class::isInstance)
                .map(Container.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the edit did not carry a container"));
    }

    /** Every text in an edited container, joined, so one assertion can look for a page. */
    private static String textOf(ButtonInteractionEvent event) {
        StringBuilder out = new StringBuilder();
        for (var child : edited(event).getComponents()) {
            if (child instanceof TextDisplay display) {
                out.append(display.getContent()).append('\n');
            }
            if (child instanceof ActionRow row) {
                for (var item : row.getComponents()) {
                    if (item instanceof Button button) {
                        out.append(button.getLabel()).append('\n');
                    }
                }
            }
        }
        return out.toString();
    }
}
