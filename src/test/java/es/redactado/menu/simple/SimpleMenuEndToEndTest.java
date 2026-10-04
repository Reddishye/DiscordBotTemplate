package es.redactado.menu.simple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Msg;
import es.redactado.menu.core.ComponentId;
import es.redactado.menu.core.JdaMocks;
import es.redactado.menu.core.MenuRouter;
import es.redactado.menu.core.TestRouters;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.view.ModalForm;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * A declared menu, pressed by a user, through the real router.
 *
 * <p>The whole claim of the DSL is that nothing about what it produces is special, so these
 * tests do not use a harness of their own: they register the built menu in an ordinary
 * router, take the component ids out of what it actually rendered, and press those, which is
 * what a user does. Every guarantee that predates T11, the owner check, the duplicate-click
 * guard, the single edit path, therefore applies here without being restated.
 *
 * <p>Handlers signal through a latch rather than a sleep, so a test waits for the work it
 * cares about instead of guessing how long it takes.
 */
class SimpleMenuEndToEndTest {

    private static final long MESSAGE = 900L;
    private static final long USER = 42L;
    private static final long STRANGER = 777L;

    @Test
    @DisplayName("a click runs its handler once, after the router deferred the edit")
    void clickRunsTheHandlerOnce() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch ran = new CountDownLatch(1);
        Menu menu =
                Menus.simple("shop")
                        .home(
                                v ->
                                        v.text("Shop")
                                                .row(
                                                        r ->
                                                                r.primary(
                                                                        "add",
                                                                        Msg.key("a"),
                                                                        click -> {
                                                                            runs.incrementAndGet();
                                                                            ran.countDown();
                                                                            return click.done();
                                                                        })))
                        .build();

        try (MenuRouter router = TestRouters.with(menu)) {
            ButtonInteractionEvent event = press(router, menu, "home", "add");

            assertThat(router.dispatchButton(event)).as("the id came from our own menu").isTrue();

            verify(event, timeout(5_000)).deferEdit();
            assertThat(ran.await(5, TimeUnit.SECONDS)).as("the handler ran").isTrue();
            assertThat(runs.get()).as("one press, one run").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("refresh redraws the view that owns the action that was pressed")
    void refreshRedrawsTheOwningView() {
        Menu menu =
                Menus.simple("shop")
                        .home(
                                v ->
                                        v.text("Home")
                                                .row(
                                                        r ->
                                                                r.primary(
                                                                        "bump",
                                                                        Msg.key("a"),
                                                                        click -> {
                                                                            click.ctx()
                                                                                    .session()
                                                                                    .putState(
                                                                                            "bumped",
                                                                                            true);
                                                                            return click.refresh();
                                                                        })))
                        .view("detail", v -> v.text("Detail"))
                        .build();

        try (MenuRouter router = TestRouters.with(menu)) {
            ButtonInteractionEvent event = press(router, menu, "home", "bump");

            assertThat(router.dispatchButton(event)).isTrue();

            assertThat(editedText(event.getHook()))
                    .as("'bump' belongs to home, so home is what a refresh redraws")
                    .contains("Home")
                    .doesNotContain("Detail");
        }
    }

    @Test
    @DisplayName("refresh on a second view redraws that view, not the home view")
    void refreshAfterMovingBetweenViews() {
        Menu menu =
                Menus.simple("shop")
                        .home(v -> v.text("Home").row(r -> r.view("detail", "Detail")))
                        .view(
                                "detail",
                                v ->
                                        v.text("Detail")
                                                .row(
                                                        r ->
                                                                r.primary(
                                                                        "bump",
                                                                        Msg.key("a"),
                                                                        click -> click.refresh())))
                        .build();

        try (MenuRouter router = TestRouters.with(menu)) {
            // The user is two views deep; the action names the button, not the screen.
            ButtonInteractionEvent event = press(router, menu, "detail", "bump");

            assertThat(router.dispatchButton(event)).isTrue();

            assertThat(editedText(event.getHook()))
                    .as("'bump' belongs to detail, and that is what comes back")
                    .contains("Detail")
                    .doesNotContain("Home");
        }
    }

    @Test
    @DisplayName("go pushes a view, keeping the one underneath in history")
    void goPushesAView() {
        Menu menu =
                Menus.simple("shop")
                        .home(
                                v ->
                                        v.text("Home")
                                                .row(
                                                        r ->
                                                                r.primary(
                                                                        "open",
                                                                        Msg.key("a"),
                                                                        click ->
                                                                                click.go(
                                                                                        "detail"))))
                        .view("detail", v -> v.text("Detail").row(r -> r.back()))
                        .build();

        try (MenuRouter router = TestRouters.with(menu)) {
            ButtonInteractionEvent event = press(router, menu, "home", "open");

            assertThat(router.dispatchButton(event)).isTrue();

            assertThat(editedText(event.getHook()))
                    .as("go pushed the detail view")
                    .contains("Detail");
        }
    }

    @Test
    @DisplayName("the back button goes to the view underneath")
    void backReturns() {
        Menu menu =
                Menus.simple("shop")
                        .home(v -> v.text("Home").row(r -> r.view("detail", "Detail")))
                        .view("detail", v -> v.text("Detail").row(r -> r.back()))
                        .build();

        try (MenuRouter router = TestRouters.with(menu)) {
            // Push the view for real, so the history the Back reads is the one a user built.
            ButtonInteractionEvent push = press(router, menu, "home", "nav");
            assertThat(router.dispatchButton(push)).isTrue();
            editedText(push.getHook());

            ButtonInteractionEvent event = press(router, menu, "detail", "nav");
            assertThat(router.dispatchButton(event)).isTrue();

            assertThat(editedText(event.getHook()))
                    .as("back returns to the view that was on screen before")
                    .contains("Home");
        }
    }

    @Test
    @DisplayName("a select delivers the values the user chose, not the labels")
    void selectDeliversValues() throws Exception {
        AtomicReference<List<String>> holder = new AtomicReference<>();
        CountDownLatch ran = new CountDownLatch(1);
        Menu menu =
                Menus.simple("shop")
                        .home(
                                v ->
                                        v.text("Shop")
                                                .select(
                                                        "section",
                                                        Msg.key("a"),
                                                        o ->
                                                                o.option("rules", "Rules")
                                                                        .option(
                                                                                "faq",
                                                                                "FAQ",
                                                                                "Most asked"),
                                                        pick -> {
                                                            holder.set(pick.values());
                                                            ran.countDown();
                                                            return pick.done();
                                                        }))
                        .build();

        try (MenuRouter router = TestRouters.with(menu)) {
            String id = selectOf(render(menu, "home")).getCustomId();
            StringSelectInteractionEvent event = JdaMocks.select(id, true, MESSAGE, USER, "faq");

            assertThat(router.dispatchSelect(event)).isTrue();
            verify(event, timeout(5_000)).deferEdit();
            assertThat(ran.await(5, TimeUnit.SECONDS)).as("the handler ran").isTrue();

            assertThat(holder.get())
                    .as("the handler reads the value, the user read the label")
                    .containsExactly("faq");
        }
    }

    @Test
    @DisplayName("a modal button opens the form, and the answers reach the handler trimmed")
    void modalRoundTrip() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        CountDownLatch ran = new CountDownLatch(1);
        Menu menu =
                Menus.simple("shop")
                        .home(
                                v ->
                                        v.text("Shop")
                                                .row(
                                                        r ->
                                                                r.primary(
                                                                                "rename",
                                                                                Msg.key("a"),
                                                                                click -> {
                                                                                    ModalForm form =
                                                                                            ModalForm
                                                                                                    .create(
                                                                                                            click
                                                                                                                    .ctx(),
                                                                                                            "renameForm",
                                                                                                            "Rename");
                                                                                    form.shortField(
                                                                                            "name",
                                                                                            "Name");
                                                                                    click.modal(
                                                                                            form
                                                                                                    .build());
                                                                                    return click
                                                                                            .done();
                                                                                })
                                                                        .opensModal()))
                        .onSubmit(
                                "renameForm",
                                submit -> {
                                    seen.set(submit.values().get("name"));
                                    ran.countDown();
                                    return submit.done();
                                })
                        .build();

        try (MenuRouter router = TestRouters.with(menu)) {
            ButtonInteractionEvent open =
                    JdaMocks.button(buttonId(render(menu, "home"), "rename"), false, MESSAGE, USER);

            assertThat(router.dispatchButton(open)).isTrue();

            verify(open, timeout(5_000)).replyModal(any(Modal.class));
            verify(open, never()).deferEdit();

            // The mappings are built before the stubbing starts: Mockito cannot record a
            // stub while another stubbing is in progress.
            List<net.dv8tion.jda.api.interactions.modals.ModalMapping> values =
                    List.of(mapping("name", "  Ada  "));
            ModalInteractionEvent submitted =
                    JdaMocks.modal(ComponentId.encode("shop", "renameForm"), true);
            when(submitted.getValues()).thenReturn(values);

            assertThat(router.dispatchModal(submitted)).isTrue();
            assertThat(ran.await(5, TimeUnit.SECONDS))
                    .as("the submission reached the handler")
                    .isTrue();

            assertThat(seen.get())
                    .as("trimmed on the way in, whatever the client sent")
                    .isEqualTo("Ada");
        }
    }

    @Test
    @DisplayName("a user who is not the owner cannot press a personal menu")
    void foreignUserIsDenied() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        Menu menu =
                Menus.simple("shop")
                        .home(
                                v ->
                                        v.text("Shop")
                                                .row(
                                                        r ->
                                                                r.primary(
                                                                        "add",
                                                                        Msg.key("a"),
                                                                        click -> {
                                                                            ran.countDown();
                                                                            return click.done();
                                                                        })))
                        .build();

        try (MenuRouter router = TestRouters.with(menu)) {
            // The message belongs to STRANGER; this press comes from the fixed clicker.
            ButtonInteractionEvent event =
                    JdaMocks.button(
                            buttonId(render(menu, "home"), "add"), false, MESSAGE, STRANGER);

            assertThat(router.dispatchButton(event)).isTrue();

            ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
            verify(event, timeout(5_000)).reply(reply.capture());
            assertThat(reply.getValue())
                    .as("the framework's own sentence, not a silent no-op")
                    .isEqualTo("This menu is not yours.");
            verify(event, never()).deferEdit();
            assertThat(ran.await(300, TimeUnit.MILLISECONDS))
                    .as("the handler must not run for a user who does not own the message")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("a second press on a message already being redrawn is dropped")
    void duplicateClickIsDropped() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch ran = new CountDownLatch(1);
        Menu menu =
                Menus.simple("shop")
                        .home(
                                v ->
                                        v.text("Shop")
                                                .row(
                                                        r ->
                                                                r.primary(
                                                                        "add",
                                                                        Msg.key("a"),
                                                                        click -> {
                                                                            runs.incrementAndGet();
                                                                            ran.countDown();
                                                                            return click.done();
                                                                        })))
                        .build();

        try (MenuRouter router = TestRouters.with(menu)) {
            String id = buttonId(render(menu, "home"), "add");
            ButtonInteractionEvent first = JdaMocks.button(id, true, MESSAGE, USER);
            ButtonInteractionEvent second = JdaMocks.button(id, true, MESSAGE, USER);

            assertThat(router.dispatchButton(first)).isTrue();
            assertThat(router.dispatchButton(second))
                    .as("the guard still owns the message, because the DSL is not a new system")
                    .isTrue();

            assertThat(ran.await(5, TimeUnit.SECONDS)).as("the first press ran").isTrue();
            Thread.sleep(200);
            assertThat(runs.get())
                    .as("the second press found the message already claimed")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("reply sends an ephemeral follow-up through the hook")
    void replyIsEphemeral() {
        Menu menu =
                Menus.simple("shop")
                        .home(
                                v ->
                                        v.text("Shop")
                                                .row(
                                                        r ->
                                                                r.primary(
                                                                        "add",
                                                                        Msg.key("a"),
                                                                        click -> {
                                                                            click.reply(
                                                                                    Msg.key(
                                                                                            "menu.nav.back"));
                                                                            return click.done();
                                                                        })))
                        .build();

        try (MenuRouter router = TestRouters.with(menu)) {
            ButtonInteractionEvent event = press(router, menu, "home", "add");

            assertThat(router.dispatchButton(event)).isTrue();

            verify(event.getHook(), timeout(5_000))
                    .sendMessage(argThat((String message) -> "Back".equals(message)));
        }
    }

    // ---------------------------------------------------------------- fixtures

    private static ButtonInteractionEvent press(
            MenuRouter router, Menu menu, String view, String action) {
        return JdaMocks.button(buttonId(render(menu, view), action), true, MESSAGE, USER);
    }

    private static Container render(Menu menu, String view) {
        return menu.render(context(menu.id(), view)).join();
    }

    /**
     * The component id of the button that runs an action.
     *
     * <p>Matched on the action inside the id rather than on the label, because the label is
     * a message that resolves per reader while the action is the part the router dispatches
     * on.
     */
    private static String buttonId(Container container, String action) {
        for (Button button : buttons(container)) {
            ComponentId id = ComponentId.decode(button.getCustomId()).orElse(null);
            if (id != null && action.equals(id.action())) {
                return button.getCustomId();
            }
        }
        throw new AssertionError("no button for action " + action);
    }

    private static List<Button> buttons(Container container) {
        List<Button> found = new java.util.ArrayList<>();
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

    /** One submitted modal field. */
    private static net.dv8tion.jda.api.interactions.modals.ModalMapping mapping(
            String id, String value) {
        net.dv8tion.jda.api.interactions.modals.ModalMapping mapping =
                mock(net.dv8tion.jda.api.interactions.modals.ModalMapping.class);
        when(mapping.getCustomId()).thenReturn(id);
        when(mapping.getAsString()).thenReturn(value);
        return mapping;
    }

    /** A context that renders with the default preset, built without a gateway. */
    private static MenuContext context(String menuId, String view) {
        MenuContext ctx = mock(MenuContext.class, CALLS_REAL_METHODS);
        when(ctx.menuId()).thenReturn(menuId);
        when(ctx.action()).thenReturn(view);
        when(ctx.params()).thenReturn(List.of());
        when(ctx.preset()).thenReturn(BuiltinPresets.DEFAULT);
        when(ctx.locale()).thenReturn(Locale.ENGLISH);
        when(ctx.t(anyString(), any(Object[].class)))
                .thenAnswer(
                        call -> {
                            Object[] all = call.getArguments();
                            Object[] args = new Object[all.length - 1];
                            System.arraycopy(all, 1, args, 0, args.length);
                            return es.redactado.menu.core.Messages.standard()
                                    .get(Locale.ENGLISH, (String) all[0], args);
                        });
        return ctx;
    }

    /**
     * The container the interaction's hook was asked to write, as text.
     *
     * <p>Captured through the varargs overload because that is the one the framework's edit
     * path binds to when it passes a single container.
     */
    private static String editedText(net.dv8tion.jda.api.interactions.InteractionHook hook) {
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
