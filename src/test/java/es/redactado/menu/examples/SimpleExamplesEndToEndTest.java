package es.redactado.menu.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Session;
import es.redactado.menu.core.ComponentId;
import es.redactado.menu.core.JdaMocks;
import es.redactado.menu.core.MenuRouter;
import es.redactado.menu.core.Messages;
import es.redactado.menu.core.TestRouters;
import es.redactado.menu.preset.BuiltinPresets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Each example pressed through the real router, as a user would.
 *
 * <p>An example that renders is not an example that works: this is what proves the buttons in
 * {@link HelpMenu}, the session state in {@link CounterMenu} and the loader, pager and
 * select in {@link ServerInfoMenu} are all wired to something.
 */
class SimpleExamplesEndToEndTest {

    private static final long MESSAGE = 900L;
    private static final long USER = 42L;

    @Test
    @DisplayName("the help menu opens its second view and comes back")
    void helpNavigatesAndComesBack() {
        Menu menu = HelpMenu.build();

        try (MenuRouter router = TestRouters.with(menu)) {
            ButtonInteractionEvent open =
                    JdaMocks.button(
                            idOf(render(menu, HelpMenu.HOME), "open_faq"), true, MESSAGE, USER);
            assertThat(router.dispatchButton(open)).isTrue();
            assertThat(editedText(open.getHook()))
                    .as("the topic button pushed the FAQ view")
                    .contains("Frequently asked");

            Container faq = render(menu, HelpMenu.FAQ);
            ButtonInteractionEvent back = JdaMocks.button(idOf(faq, "nav"), true, MESSAGE, USER);
            assertThat(router.dispatchButton(back)).isTrue();
            assertThat(editedText(back.getHook()))
                    .as("Back returns to the view underneath, whose header is the home one")
                    .contains("Help")
                    .doesNotContain("Frequently asked");
        }
    }

    @Test
    @DisplayName("the help menu's link is a real Discord link, not an action of the menu")
    void helpLinkIsNotAnAction() {
        Container container = render(HelpMenu.build(), HelpMenu.HOME);

        Button link = linkOf(container);
        assertThat(link.getUrl()).isEqualTo("https://example.com/docs");
        assertThat(ComponentId.isMenuId(link.getCustomId()))
                .as("a link leaves the menu system entirely, so the router must ignore it")
                .isFalse();
    }

    @Test
    @DisplayName("the counter counts in the session and survives the redraw")
    void counterCountsInTheSession() {
        Menu menu = CounterMenu.build();

        try (MenuRouter router = TestRouters.with(menu)) {
            Container home = render(menu, CounterMenu.HOME);
            assertThat(textOf(home)).as("a fresh session starts at zero").contains("Count: 0");

            ButtonInteractionEvent plus = JdaMocks.button(idOf(home, "plus"), true, MESSAGE, USER);
            assertThat(router.dispatchButton(plus)).isTrue();
            assertThat(editedText(plus.getHook())).as("one press, one more").contains("Count: 1");

            Container after = render(menu, CounterMenu.HOME);
            ButtonInteractionEvent minus =
                    JdaMocks.button(idOf(after, "minus"), true, MESSAGE, USER);
            assertThat(router.dispatchButton(minus)).isTrue();
            assertThat(editedText(minus.getHook())).contains("Count: 0");
        }
    }

    @Test
    @DisplayName("the counter's reset is behind a confirmation, and cancelling changes nothing")
    void counterResetIsConfirmed() {
        Menu menu = CounterMenu.build();

        try (MenuRouter router = TestRouters.with(menu)) {
            Container home = render(menu, CounterMenu.HOME);
            ButtonInteractionEvent plus = JdaMocks.button(idOf(home, "plus"), true, MESSAGE, USER);
            assertThat(router.dispatchButton(plus)).isTrue();
            editedText(plus.getHook());

            ButtonInteractionEvent ask =
                    JdaMocks.button(
                            idOf(render(menu, CounterMenu.HOME), "ask_reset"), true, MESSAGE, USER);
            assertThat(router.dispatchButton(ask)).isTrue();
            assertThat(editedText(ask.getHook()))
                    .as("the destructive button opens the question rather than doing it")
                    .contains("Reset the counter");

            ButtonInteractionEvent cancel =
                    JdaMocks.button(
                            idOf(render(menu, CounterMenu.CONFIRM), "nav"), true, MESSAGE, USER);
            assertThat(router.dispatchButton(cancel)).isTrue();
            assertThat(editedText(cancel.getHook()))
                    .as("cancelling leaves the count alone")
                    .contains("Count: 1");
        }
    }

    @Test
    @DisplayName("the counter's confirmation resets when pressed")
    void counterResetRuns() {
        Menu menu = CounterMenu.build();

        try (MenuRouter router = TestRouters.with(menu)) {
            ButtonInteractionEvent plus =
                    JdaMocks.button(
                            idOf(render(menu, CounterMenu.HOME), "plus"), true, MESSAGE, USER);
            assertThat(router.dispatchButton(plus)).isTrue();
            editedText(plus.getHook());

            Button confirm = buttonOf(render(menu, CounterMenu.CONFIRM), "do_reset");
            ButtonInteractionEvent reset =
                    JdaMocks.button(confirm.getCustomId(), true, MESSAGE, USER);
            assertThat(router.dispatchButton(reset)).isTrue();

            assertThat(editedText(reset.getHook()))
                    .as("the reset lands on the counter, not on the question")
                    .contains("Count: 0");
        }
    }

    @Test
    @DisplayName("the server info menu loads its data and lists it a page at a time")
    void serverInfoLoadsAndPages() {
        Menu menu = ServerInfoMenu.build(new ServerInfoMenu.FakeService(20));

        try (MenuRouter router = TestRouters.with(menu)) {
            Container first = render(menu, ServerInfoMenu.HOME);
            assertThat(textOf(first))
                    .as("the first page of members, and the data behind them")
                    .contains("Example guild")
                    .contains("Ada - admin")
                    .contains("Alan - member")
                    .doesNotContain("Katherine");
            assertThat(buttonsOf(first))
                    .as(
                            "six members over five to a page: prev, the page indicator, next, and"
                                    + " the menu's own button")
                    .hasSize(4);

            String id = pageButtonIdOf(first, "next");
            ButtonInteractionEvent next = JdaMocks.button(id, true, MESSAGE, USER);
            assertThat(router.dispatchButton(next)).isTrue();
            assertThat(editedText(next.getHook()))
                    .as("the second page, from the state the session kept")
                    .contains("Katherine - moderator");
        }
    }

    @Test
    @DisplayName("the server info select switches the section shown, without navigating")
    void serverInfoSelectSwitchesSection() {
        Menu menu = ServerInfoMenu.build(new ServerInfoMenu.FakeService(1));

        try (MenuRouter router = TestRouters.with(menu)) {
            Container home = render(menu, ServerInfoMenu.HOME);
            assertThat(textOf(home)).as("the overview is what it opens on").contains("owner Ada");

            StringSelectMenu select = selectOf(home);
            assertThat(select.getCustomId())
                    .as("the select carries the action the DSL declared for it")
                    .endsWith(":section");

            var event =
                    JdaMocks.select(
                            select.getCustomId(),
                            true,
                            MESSAGE,
                            USER,
                            ServerInfoMenu.sections().get(2));
            assertThat(router.dispatchSelect(event)).isTrue();

            assertThat(editedText(event.getHook()))
                    .as("the roles section, drawn by the same view")
                    .contains("Roles in use: admin, moderator, member");
        }
    }

    @Test
    @DisplayName("the server info loader runs off the event thread, and a slow one still answers")
    void serverInfoLoaderIsAsynchronous() {
        Menu menu = ServerInfoMenu.build(new ServerInfoMenu.FakeService(150));

        try (MenuRouter router = TestRouters.with(menu)) {
            Container container = render(menu, ServerInfoMenu.HOME);

            assertThat(textOf(container))
                    .as("the render waited for the model")
                    .contains("Example guild");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Container render(Menu menu, String view) {
        MenuContext ctx = mock(MenuContext.class);
        Session session = new Session();
        when(ctx.menuId()).thenReturn(menu.id());
        when(ctx.action()).thenReturn(view);
        when(ctx.params()).thenReturn(List.of());
        when(ctx.preset()).thenReturn(BuiltinPresets.DEFAULT);
        when(ctx.locale()).thenReturn(Locale.ENGLISH);
        when(ctx.session()).thenReturn(session);
        when(ctx.findSession()).thenReturn(Optional.of(session));
        when(ctx.userId()).thenReturn(String.valueOf(USER));
        when(ctx.t(anyString(), any(Object[].class)))
                .thenAnswer(
                        call -> {
                            Object[] all = call.getArguments();
                            Object[] args = new Object[all.length - 1];
                            System.arraycopy(all, 1, args, 0, args.length);
                            return Messages.standard().get(Locale.ENGLISH, (String) all[0], args);
                        });
        return menu.render(ctx).join();
    }

    /** The component id of a pager button, whose id carries the direction and the list id. */
    private static String pageButtonIdOf(Container container, String direction) {
        for (Button button : buttonsOf(container)) {
            ComponentId id = ComponentId.decode(button.getCustomId()).orElse(null);
            if (id != null
                    && "page".equals(id.action())
                    && direction.equals(id.params().getFirst())) {
                return button.getCustomId();
            }
        }
        throw new AssertionError("no " + direction + " button");
    }

    /** The component id of the button that runs an action. */
    private static String idOf(Container container, String action) {
        return buttonOf(container, action).getCustomId();
    }

    private static Button buttonOf(Container container, String action) {
        for (Button button : buttonsOf(container)) {
            ComponentId id = ComponentId.decode(button.getCustomId()).orElse(null);
            if (id != null && action.equals(id.action())) {
                return button;
            }
        }
        throw new AssertionError("no button for action " + action);
    }

    private static List<Button> buttonsOf(Container container) {
        List<Button> found = new ArrayList<>();
        for (Object child : container.getComponents()) {
            if (child instanceof ActionRow row) {
                for (Object item : row.getComponents()) {
                    if (item instanceof Button button) {
                        found.add(button);
                    }
                }
            }
        }
        return found;
    }

    private static Button linkOf(Container container) {
        for (Button button : buttonsOf(container)) {
            if (button.getUrl() != null) {
                return button;
            }
        }
        throw new AssertionError("the view rendered no link");
    }

    private static StringSelectMenu selectOf(Container container) {
        for (Object child : container.getComponents()) {
            if (child instanceof ActionRow row) {
                for (Object item : row.getComponents()) {
                    if (item instanceof StringSelectMenu select) {
                        return select;
                    }
                }
            }
        }
        throw new AssertionError("the view rendered no select");
    }

    /** The container the hook was asked to write, as text. */
    private static String editedText(InteractionHook hook) {
        ArgumentCaptor<MessageTopLevelComponent[]> captor =
                ArgumentCaptor.forClass(MessageTopLevelComponent[].class);
        verify(hook, timeout(5_000)).editOriginalComponents(captor.capture());

        StringBuilder out = new StringBuilder();
        for (MessageTopLevelComponent component : captor.getValue()) {
            if (component instanceof Container container) {
                out.append(textOf(container));
            }
        }
        return out.toString();
    }

    private static String textOf(Container container) {
        StringBuilder out = new StringBuilder();
        for (Object child : container.getComponents()) {
            if (child instanceof TextDisplay display) {
                out.append(display.getContent()).append('\n');
            }
        }
        return out.toString();
    }
}
