package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.Done;
import es.redactado.menu.api.MenuContext;
import java.util.Optional;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AbstractMenuTest {

    /** A minimal subclass that declares no actions of its own. */
    private static class BareMenu extends AbstractMenu {
        BareMenu() {
            super("bare");
        }

        @Override
        protected void declare(ActionTable.Builder table) {}

        @Override
        protected Container build(MenuContext ctx) {
            return Container.of(TextDisplay.of("body"));
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
        protected Container build(MenuContext ctx) {
            return Container.of(TextDisplay.of("body"));
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
        MenuContext ctx = mock(MenuContext.class);
        when(ctx.event()).thenReturn(unacknowledged);

        AbstractMenu menu = new BareMenu();

        assertThatThrownBy(() -> menu.refresh(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ack mode");
    }

    @Test
    @DisplayName("handleBack throws when the interaction is not acknowledged")
    void handleBackNeedsAcknowledgement() {
        MenuContext ctx = mock(MenuContext.class);
        ButtonInteractionEvent event = JdaMocks.button("menu:bare:nav", false);

        AbstractMenu menu = new BareMenu();

        assertThatThrownBy(() -> menu.handleBack(ctx, event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ack mode");
    }

    @Test
    @DisplayName("showModal throws when the interaction is already acknowledged")
    void showModalNeedsUnacknowledged() {
        ButtonInteractionEvent acknowledged = JdaMocks.button("menu:bare:open", true);
        MenuContext ctx = mock(MenuContext.class);
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
    @DisplayName("handleBack sends an ephemeral hook message when there is nothing to go back to")
    void handleBackWithoutPrevious() {
        MenuContext ctx = mock(MenuContext.class);
        when(ctx.pop()).thenReturn(Optional.empty());
        ButtonInteractionEvent event = JdaMocks.button("menu:bare:nav", true);

        new BareMenu().handleBack(ctx, event);

        verify(event.getHook()).sendMessage(anyString());
    }

    @Test
    @DisplayName("handleBack edits the original message when there is a previous menu")
    void handleBackWithPrevious() {
        MenuContext ctx = mock(MenuContext.class);
        MenuContext previous = mock(MenuContext.class);
        when(ctx.pop()).thenReturn(Optional.of(previous));
        ButtonInteractionEvent event = JdaMocks.button("menu:bare:nav", true);

        new BareMenu().handleBack(ctx, event);

        verify(event.getHook()).editOriginalComponents(any(MessageTopLevelComponent[].class));
        verify(event.getHook(), never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("nav handler is bound so it returns a completed future")
    void navHandlerCompletes() {
        MenuContext ctx = mock(MenuContext.class);
        when(ctx.pop()).thenReturn(Optional.empty());

        ActionTable table = tableOf(new BareMenu());
        var handler = table.button("nav").orElseThrow().handler();

        assertThat(handler.handle(ctx, JdaMocks.button("menu:bare:nav", true))).isEqualTo(Done.NOW);
    }
}
