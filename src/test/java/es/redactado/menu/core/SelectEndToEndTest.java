package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.view.MenuBuilder;
import es.redactado.menu.view.Row;
import es.redactado.menu.view.SelectMenu;
import es.redactado.menu.view.Text;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Choosing from a select through the router, as a user would.
 *
 * <p>Separate from {@code MenuRouterSelectTest} because that one hands the router an id it
 * wrote by hand, while this one takes the id from a rendered view. That gap is the one
 * worth covering: a select whose id named an action the menu never declared would satisfy
 * every dispatch test in the package and do nothing at all when a user picks an option.
 */
class SelectEndToEndTest {

    private static final int AWAIT_MS = 5_000;
    private static final long MESSAGE = 900L;
    private static final long USER = 42L;

    /** Where the fixture remembers what was chosen, inside the session. */
    private static final String CHOSEN = "chosen";

    @Test
    @DisplayName("the id from a rendered select routes to the action it names")
    void renderedIdRoutesToItsAction() {
        SelectFixture menu = new SelectFixture(Ack.DEFER_EDIT);
        try (MenuRouter router = TestRouters.with(menu)) {
            StringSelectInteractionEvent event = submit(router, menu, "member", "guest");

            assertThat(router.dispatchSelect(event))
                    .as("the id came from our own menu, so the router must consume it")
                    .isTrue();
            awaitIdle(router);

            assertThat(menu.seen.get()).containsExactly("member", "guest");
        }
    }

    @Test
    @DisplayName("the handler receives values, while the user was reading labels")
    void handlerReceivesValuesNotLabels() {
        SelectFixture menu = new SelectFixture(Ack.DEFER_EDIT);
        try (MenuRouter router = TestRouters.with(menu)) {
            StringSelectInteractionEvent event = submit(router, menu, "owner");

            assertThat(router.dispatchSelect(event)).isTrue();
            awaitIdle(router);

            assertThat(labelsOf(menu.view(context())))
                    .as("the user picked the option that reads 'Owner'")
                    .containsExactly("Owner", "Member");
            assertThat(menu.seen.get()).as("the handler gets its value").containsExactly("owner");
        }
    }

    @Test
    @DisplayName("a select declaring DEFER_EDIT is acknowledged before the handler runs")
    void ackIsPerformedBeforeTheHandler() {
        SelectFixture menu = new SelectFixture(Ack.DEFER_EDIT);
        try (MenuRouter router = TestRouters.with(menu)) {
            StringSelectInteractionEvent event = submit(router, menu, "member");

            assertThat(router.dispatchSelect(event)).isTrue();

            verify(event, timeout(AWAIT_MS)).deferEdit();
            awaitIdle(router);
        }

        assertThat(menu.seen.get()).containsExactly("member");
    }

    @Test
    @DisplayName("a select declaring NONE is left unanswered for the handler to do")
    void ackNoneIsLeftToTheHandler() {
        SelectFixture menu = new SelectFixture(Ack.NONE);
        try (MenuRouter router = TestRouters.with(menu)) {
            StringSelectInteractionEvent event = submit(router, menu, "member");

            assertThat(router.dispatchSelect(event)).isTrue();
            awaitIdle(router);
        }

        verify(menu.last, never()).deferEdit();
        assertThat(menu.seen.get()).containsExactly("member");
    }

    @Test
    @DisplayName("the handler re-renders the message with the choice applied")
    void handlerReRendersTheMessage() {
        SelectFixture menu = new SelectFixture(Ack.DEFER_EDIT);
        try (MenuRouter router = TestRouters.with(menu)) {
            StringSelectInteractionEvent event = submit(router, menu, "member");

            assertThat(router.dispatchSelect(event)).isTrue();

            assertThat(editedText(event))
                    .as("the message the user is looking at shows the choice")
                    .contains("Chosen: member");
        }
    }

    // ------------------------------------------------------------- the menu

    /** One select and one declared select action, which is all these tests need. */
    private static final class SelectFixture extends AbstractMenu {

        private final Ack ack;
        private final AtomicReference<List<String>> seen = new AtomicReference<>();
        private StringSelectInteractionEvent last;

        SelectFixture(Ack ack) {
            super("assign");
            this.ack = ack;
        }

        @Override
        protected void declare(ActionTable.Builder table) {
            table.select(
                    "role",
                    ack,
                    (ctx, event) -> {
                        last = event;
                        seen.set(event.getValues());
                        ctx.session().putState(CHOSEN, String.join(",", event.getValues()));
                        return refresh(ctx);
                    });
        }

        @Override
        public CompletableFuture<Container> render(MenuContext ctx) {
            return CompletableFuture.completedFuture(view(ctx));
        }

        Container view(MenuContext ctx) {
            MenuBuilder builder =
                    MenuBuilder.create("assign")
                            .add(
                                    Row.of(
                                            SelectMenu.of("role", "Pick a role")
                                                    .option("owner", "Owner")
                                                    .option("member", "Member")));
            // Read back from the session, so the re-render proves the choice survived
            // the handler rather than proving the handler ran.
            ctx.findSession()
                    .flatMap(session -> session.state(CHOSEN, String.class))
                    .ifPresent(chosen -> builder.add(Text.of("Chosen: " + chosen)));
            return builder.build(ctx);
        }
    }

    // ------------------------------------------------------------- helpers

    /** A submission carrying the id the menu really renders. */
    private static StringSelectInteractionEvent submit(
            MenuRouter router, SelectFixture menu, String... values) {
        String id = selectOf(menu.view(context())).getCustomId();
        // Already acknowledged, which is the state a handler finds it in: the router has
        // run the declared ack by the time the handler body starts.
        return JdaMocks.select(id, true, MESSAGE, USER, values);
    }

    /** A context that renders with the default preset, built without a gateway. */
    private static MenuContext context() {
        MenuContext ctx = mock(MenuContext.class);
        when(ctx.menuId()).thenReturn("assign");
        when(ctx.preset()).thenReturn(BuiltinPresets.DEFAULT);
        return ctx;
    }

    /**
     * The container the interaction's hook was asked to write, as text.
     *
     * <p>Captured through the varargs overload because that is the one {@code ViewEditor}
     * binds to when it passes a single container.
     */
    private static String editedText(StringSelectInteractionEvent event) {
        ArgumentCaptor<MessageTopLevelComponent[]> captor =
                ArgumentCaptor.forClass(MessageTopLevelComponent[].class);
        verify(event.getHook(), timeout(AWAIT_MS)).editOriginalComponents(captor.capture());

        StringBuilder out = new StringBuilder();
        for (MessageTopLevelComponent component : captor.getValue()) {
            if (component instanceof Container container) {
                for (var child : container.getComponents()) {
                    if (child instanceof TextDisplay display) {
                        out.append(display.getContent()).append('\n');
                    }
                }
            }
        }
        return out.toString();
    }

    private static List<String> labelsOf(Container container) {
        return selectOf(container).getOptions().stream().map(SelectOption::getLabel).toList();
    }

    private static StringSelectMenu selectOf(Container container) {
        for (var child : container.getComponents()) {
            if (child instanceof ActionRow row) {
                return row.getComponents().stream()
                        .filter(StringSelectMenu.class::isInstance)
                        .map(StringSelectMenu.class::cast)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("the view rendered no select"));
            }
        }
        throw new AssertionError("the view rendered no action row");
    }

    private static void awaitIdle(MenuRouter router) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_MS);
        while (router.inFlight() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(router.inFlight()).as("no interaction is still claimed").isZero();
    }
}
