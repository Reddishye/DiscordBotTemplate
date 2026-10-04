package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavEntry;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.view.ActionButton;
import es.redactado.menu.view.MenuBuilder;
import es.redactado.menu.view.Row;
import es.redactado.menu.view.Text;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * A refresh redraws the view on screen, not the action that was pressed.
 *
 * <p>This is the rule a hand-written menu gets wrong, because the mistake is invisible until
 * a form is submitted: the handler is reached through an action called {@code save_birth},
 * which is not a view, so rendering the context as it stands asks the menu for a view named
 * {@code save_birth} and the user is told the view is not available straight after their change
 * was saved.
 *
 * <p>The profile example shipped exactly that, and the fix belongs in the base class rather
 * than in every menu, because "redraw what I am showing" is what every caller of
 * {@code refresh} means.
 */
class RefreshCurrentViewTest {

    private static final long MESSAGE = 900L;
    private static final long USER = 42L;

    @Test
    @DisplayName("a refresh after an action that is not a view redraws the view on screen")
    void refreshAfterANonViewAction() {
        TwoViewMenu menu = new TwoViewMenu();
        try (MenuRouter router = TestRouters.with(menu)) {
            // The action is "save", which is not a view this menu has, and the view on screen
            // is home. Before the rule existed this rendered "save" and failed.
            ButtonInteractionEvent event = press(menu, TwoViewMenu.HOME, TwoViewMenu.SAVE_HOME);
            assertThat(router.dispatchButton(event)).isTrue();

            assertThat(editedText(event.getHook()))
                    .as("the view the user was looking at")
                    .contains("Home")
                    .doesNotContain("Detail");
            assertThat(menu.refreshes()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("a refresh from a second view redraws that view")
    void refreshFromAnotherView() {
        TwoViewMenu menu = new TwoViewMenu();
        try (MenuRouter router = TestRouters.with(menu)) {
            ButtonInteractionEvent event = press(menu, TwoViewMenu.DETAIL, TwoViewMenu.SAVE_DETAIL);
            assertThat(router.dispatchButton(event)).isTrue();

            assertThat(editedText(event.getHook()))
                    .as("the second view is on screen, so the second view comes back")
                    .contains("Detail");
        }
    }

    @Test
    @DisplayName("a submission refreshes the view too, which is where the bug was found")
    void refreshAfterASubmission() {
        TwoViewMenu menu = new TwoViewMenu();
        try (MenuRouter router = TestRouters.with(menu)) {
            net.dv8tion.jda.api.events.interaction.ModalInteractionEvent submit =
                    JdaMocks.modal(
                            es.redactado.menu.core.ComponentId.encode(
                                    TwoViewMenu.ID, TwoViewMenu.SUBMIT),
                            true);
            // Built before the stubbing starts: Mockito cannot record a stub while another
            // stubbing is in progress.
            List<net.dv8tion.jda.api.interactions.modals.ModalMapping> values =
                    List.of(field("name", "Ada"));
            when(submit.getValues()).thenReturn(values);

            assertThat(router.dispatchModal(submit)).isTrue();

            assertThat(editedText(submit.getHook()))
                    .as("a submission arrives as save, and must still redraw home")
                    .contains("Home");
        }
    }

    @Test
    @DisplayName("a single-view menu is unaffected, because its action is its view")
    void singleViewMenusAreUnaffected() {
        SingleViewMenu menu = new SingleViewMenu();
        try (MenuRouter router = TestRouters.with(menu)) {
            ButtonInteractionEvent event = press(menu, "home", "bump");
            assertThat(router.dispatchButton(event)).isTrue();

            assertThat(editedText(event.getHook())).contains("Count: 1");
        }
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * A menu with two views, one button in each and one submission that belongs to neither.
     *
     * <p>The buttons are named per view because that is what a click can tell the menu: the
     * component id says which button was pressed, never which screen it was pressed on. A menu
     * that wanted one action across two views would have to remember the view in its session,
     * which is what the showcase does.
     */
    private static final class TwoViewMenu extends AbstractMenu {

        static final String ID = "refresh";
        static final String HOME = "home";
        static final String DETAIL = "detail";
        static final String SAVE_HOME = "save_home";
        static final String SAVE_DETAIL = "save_detail";
        static final String SUBMIT = "submit";

        private final AtomicInteger refreshes = new AtomicInteger();

        TwoViewMenu() {
            super(ID);
        }

        @Override
        protected void declare(ActionTable.Builder table) {
            table.button(SAVE_HOME, Ack.DEFER_EDIT, this::save);
            table.button(SAVE_DETAIL, Ack.DEFER_EDIT, this::save);
            table.modal(SUBMIT, Ack.DEFER_EDIT, this::save);
        }

        @Override
        public CompletableFuture<Container> render(MenuContext ctx) {
            if (!HOME.equals(ctx.action()) && !DETAIL.equals(ctx.action())) {
                // The failure this test exists for: an action that is not a view must not be
                // rendered as one.
                return unknownView(ctx);
            }
            boolean home = HOME.equals(ctx.action());
            return CompletableFuture.completedFuture(
                    MenuBuilder.create(ID)
                            .add(Text.of(home ? "Home" : "Detail"))
                            .add(
                                    Row.of(
                                            ActionButton.primary(
                                                    home ? SAVE_HOME : SAVE_DETAIL, "Save"),
                                            ActionButton.secondary("go", home ? "Detail" : "Home")))
                            .build(ctx));
        }

        @Override
        public NavEntry currentView(MenuContext ctx) {
            String view =
                    switch (ctx.action()) {
                        case SAVE_DETAIL, DETAIL -> DETAIL;
                        default -> HOME;
                    };
            return new NavEntry(ID, view, ctx.params());
        }

        private CompletableFuture<Void> save(
                MenuContext ctx,
                net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
            return count(ctx);
        }

        private CompletableFuture<Void> save(
                MenuContext ctx,
                net.dv8tion.jda.api.events.interaction.ModalInteractionEvent event) {
            return count(ctx);
        }

        private CompletableFuture<Void> count(MenuContext ctx) {
            return refresh(ctx).whenComplete((value, error) -> refreshes.incrementAndGet());
        }

        int refreshes() {
            return refreshes.get();
        }
    }

    /** One view, one action, the case that never needed the rule. */
    private static final class SingleViewMenu extends AbstractMenu {

        SingleViewMenu() {
            super("single");
        }

        @Override
        protected void declare(ActionTable.Builder table) {
            table.button(
                    "bump",
                    Ack.DEFER_EDIT,
                    (ctx, event) -> {
                        int count = ctx.sessionStateOr("count", Integer.class, 0);
                        ctx.putSessionState("count", count + 1);
                        return refresh(ctx);
                    });
        }

        @Override
        public CompletableFuture<Container> render(MenuContext ctx) {
            return CompletableFuture.completedFuture(
                    MenuBuilder.create("single")
                            .add(Text.of("Count: " + ctx.sessionStateOr("count", Integer.class, 0)))
                            .add(Row.of(ActionButton.primary("bump", "Bump")))
                            .build(ctx));
        }
    }

    private static ButtonInteractionEvent press(
            es.redactado.menu.api.Menu menu, String view, String action) {
        MenuContext ctx = mock(MenuContext.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        when(ctx.menuId()).thenReturn(menu.id());
        when(ctx.action()).thenReturn(view);
        when(ctx.params()).thenReturn(List.of());
        when(ctx.preset()).thenReturn(BuiltinPresets.DEFAULT);
        when(ctx.t(anyString(), any(Object[].class)))
                .thenAnswer(call -> (String) call.getArgument(0));
        return JdaMocks.button(idOf(menu.render(ctx).join(), action), true, MESSAGE, USER);
    }

    private static net.dv8tion.jda.api.interactions.modals.ModalMapping field(
            String id, String value) {
        net.dv8tion.jda.api.interactions.modals.ModalMapping mapping =
                mock(net.dv8tion.jda.api.interactions.modals.ModalMapping.class);
        when(mapping.getCustomId()).thenReturn(id);
        when(mapping.getAsString()).thenReturn(value);
        return mapping;
    }

    private static String idOf(Container container, String action) {
        for (Object child : container.getComponents()) {
            if (child instanceof net.dv8tion.jda.api.components.actionrow.ActionRow row) {
                for (Object item : row.getComponents()) {
                    if (item instanceof net.dv8tion.jda.api.components.buttons.Button button) {
                        es.redactado.menu.core.ComponentId id =
                                es.redactado.menu.core.ComponentId.decode(button.getCustomId())
                                        .orElse(null);
                        if (id != null && action.equals(id.action())) {
                            return button.getCustomId();
                        }
                    }
                }
            }
        }
        throw new AssertionError("no button for " + action);
    }

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
}
