package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.Done;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavEntry;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.api.Render;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AbstractMenuTest {

    /** A minimal subclass that declares no actions of its own. */
    private static class BareMenu extends AbstractMenu {
        BareMenu() {
            super("bare");
        }

        @Override
        protected void declare(ActionTable.Builder table) {}

        @Override
        public CompletableFuture<Container> render(MenuContext ctx) {
            return Render.now(Container.of(TextDisplay.of("body")));
        }
    }

    /** A subclass that tries to redeclare the built-in nav action. */
    private static class GreedyMenu extends AbstractMenu {
        GreedyMenu() {
            super("greedy");
        }

        @Override
        protected void declare(ActionTable.Builder table) {
            table.button("nav", Ack.DEFER_EDIT, (ctx, event) -> Done.NOW);
        }

        @Override
        public CompletableFuture<Container> render(MenuContext ctx) {
            return Render.now(Container.of(TextDisplay.of("body")));
        }
    }

    private static ActionTable tableOf(AbstractMenu menu) {
        ActionTable.Builder builder = ActionTable.builder();
        menu.actions(builder);
        return builder.build();
    }

    @Test
    @DisplayName("registers the built-in nav action with Ack.DEFER_EDIT")
    void registersNav() {
        ActionTable table = tableOf(new BareMenu());

        assertThat(table.button("nav")).isPresent();
        assertThat(table.button("nav").orElseThrow().ack()).isEqualTo(Ack.DEFER_EDIT);
    }

    @Test
    @DisplayName("keeps actions declared by the subclass alongside nav")
    void keepsSubclassActions() {
        class WithOwn extends BareMenu {
            @Override
            protected void declare(ActionTable.Builder table) {
                table.button("own", Ack.NONE, (ctx, event) -> Done.NOW);
            }
        }

        ActionTable table = tableOf(new WithOwn());

        assertThat(table.button("nav")).isPresent();
        assertThat(table.button("own")).isPresent();
    }

    @Test
    @DisplayName("rejects a subclass that redeclares nav")
    void rejectsDuplicateNav() {
        assertThatThrownBy(() -> tableOf(new GreedyMenu()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nav")
                .hasMessageContaining("Duplicate");
    }

    @Test
    @DisplayName("refresh throws when the interaction is not acknowledged")
    void refreshNeedsAcknowledgement() {
        ButtonInteractionEvent unacknowledged = JdaMocks.button("menu:bare:nav", false);
        MenuContext ctx = mock(MenuContext.class, CALLS_REAL_METHODS);
        when(ctx.event()).thenReturn(unacknowledged);

        AbstractMenu menu = new BareMenu();

        assertThatThrownBy(() -> menu.refresh(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ack mode");
    }

    @Test
    @DisplayName("showModal throws when the interaction is already acknowledged")
    void showModalNeedsUnacknowledged() {
        ButtonInteractionEvent acknowledged = JdaMocks.button("menu:bare:open", true);
        MenuContext ctx = mock(MenuContext.class, CALLS_REAL_METHODS);
        when(ctx.event()).thenReturn(acknowledged);

        AbstractMenu menu = new BareMenu();
        Modal modal =
                Modal.create("menu:bare:open", "Title")
                        .addComponents(
                                Label.of(
                                        "Field",
                                        TextInput.create("f", TextInputStyle.SHORT).build()))
                        .build();

        assertThatThrownBy(() -> menu.showModal(ctx, modal))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ack mode");
    }

    @Test
    @DisplayName("the nav handler delegates a menu target as the target menu's home view")
    void navHandlerDelegates() {
        MenuContext ctx = mock(MenuContext.class, CALLS_REAL_METHODS);
        when(ctx.requireString(0)).thenReturn("push");
        when(ctx.param(1)).thenReturn(Optional.of("other"));
        when(ctx.navigate(eq(NavigationMode.PUSH), any(NavEntry.class))).thenReturn(Done.NOW);

        ActionTable table = tableOf(new BareMenu());
        var handler = table.button("nav").orElseThrow().handler();

        assertThat(handler.handle(ctx, JdaMocks.button("menu:bare:nav", true))).isEqualTo(Done.NOW);
        ArgumentCaptor<NavEntry> target = ArgumentCaptor.forClass(NavEntry.class);
        verify(ctx).navigate(eq(NavigationMode.PUSH), target.capture());
        assertThat(target.getValue())
                .as("an id naming only a menu means that menu's home view")
                .isEqualTo(new NavEntry("other", "home", List.of()));
    }

    @Test
    @DisplayName("the nav handler hands a view target over untouched")
    void navHandlerKeepsTheView() {
        MenuContext ctx = mock(MenuContext.class, CALLS_REAL_METHODS);
        when(ctx.requireString(0)).thenReturn("push");
        when(ctx.param(1)).thenReturn(Optional.of("other"));
        when(ctx.param(2)).thenReturn(Optional.of("detail"));
        when(ctx.params()).thenReturn(List.of("push", "other", "detail", "42"));
        when(ctx.navigate(eq(NavigationMode.PUSH), any(NavEntry.class))).thenReturn(Done.NOW);

        ActionTable table = tableOf(new BareMenu());
        var handler = table.button("nav").orElseThrow().handler();
        handler.handle(ctx, JdaMocks.button("menu:bare:nav", true));

        ArgumentCaptor<NavEntry> target = ArgumentCaptor.forClass(NavEntry.class);
        verify(ctx).navigate(eq(NavigationMode.PUSH), target.capture());
        assertThat(target.getValue()).isEqualTo(new NavEntry("other", "detail", List.of("42")));
    }

    @Test
    @DisplayName("back has no target, so it goes through the menu-only overload")
    void navHandlerDelegatesBack() {
        MenuContext ctx = mock(MenuContext.class, CALLS_REAL_METHODS);
        when(ctx.requireString(0)).thenReturn("back");
        when(ctx.navigate(NavigationMode.BACK, "")).thenReturn(Done.NOW);

        ActionTable table = tableOf(new BareMenu());
        var handler = table.button("nav").orElseThrow().handler();

        assertThat(handler.handle(ctx, JdaMocks.button("menu:bare:nav", true))).isEqualTo(Done.NOW);
        verify(ctx).navigate(NavigationMode.BACK, "");
    }

    @Test
    @DisplayName("a nav button id round trips through the action codec")
    void navButtonIdRoundTrips() {
        String id = NavigationAction.buttonId("bare", NavigationMode.PUSH, "other");

        ComponentId parsed = ComponentId.decode(id).orElseThrow();

        assertThat(parsed.menuId()).isEqualTo("bare");
        assertThat(parsed.action()).isEqualTo("nav");
        assertThat(parsed.params()).containsExactly("push", "other");
    }

    @Test
    @DisplayName("a back button id carries no target")
    void backButtonIdOmitsTarget() {
        String id = NavigationAction.buttonId("bare", NavigationMode.BACK, "");

        assertThat(ComponentId.decode(id).orElseThrow().params()).containsExactly("back");
    }
}
