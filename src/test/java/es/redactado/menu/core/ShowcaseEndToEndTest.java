package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.NavEntry;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.api.Session;
import es.redactado.menu.examples.ShowcaseMenu;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.preset.PresetRegistry;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Walks the showcase the way a user does: one real interaction event per click.
 *
 * <p>Lives in {@code core} beside the other end-to-end tests because it borrows the router
 * test helpers, which are package-private on purpose: they are stubs, not API.
 *
 * <p>The render test proves each view draws; this proves the clicks arrive. Separate events
 * matter, because the whole design rests on state living in the session rather than on the
 * event: one reused event would keep a context alive and hide that. Every step also asserts
 * exactly one edit through the hook, since a second edit is what a duplicated handler looks
 * like from Discord's side.
 */
class ShowcaseEndToEndTest {

    private static final int AWAIT_MS = 5_000;
    private static final long MESSAGE = 3_100L;
    private static final long OWNER = 42L;
    private static final long STRANGER = 7L;

    @Test
    @DisplayName("home, components, a page, back, a preset and a form each edit exactly once")
    void theWholeWalkthrough() {
        ShowcaseMenu menu = new ShowcaseMenu(new PresetRegistry());
        SessionStore sessions = new SessionStore(SessionConfig.defaults());
        try (MenuRouter router = TestRouters.withSessions(sessions)) {
            router.register("showcase", menu);

            ButtonInteractionEvent open = click("menu:showcase:nav:push:showcase", OWNER);
            assertThat(router.dispatchButton(open)).isTrue();
            assertOneEdit(open.getHook());
            assertThat(text(open.getHook())).contains("Menu showcase");
            assertDepth(router, sessions, 1, "1 on the stack");

            ButtonInteractionEvent components = click(view(ShowcaseMenu.COMPONENTS), OWNER);
            assertThat(router.dispatchButton(components)).isTrue();
            assertOneEdit(components.getHook());
            assertThat(text(components.getHook()))
                    .as("the pager starts on page one")
                    .contains("1/5")
                    .contains("Fake item 1");
            assertDepth(router, sessions, 2, "2 on the stack");

            ButtonInteractionEvent next = click(pagerId("next"), OWNER);
            assertThat(router.dispatchButton(next)).isTrue();
            assertOneEdit(next.getHook());
            assertThat(text(next.getHook())).contains("2/5").contains("Fake item 6");
            assertDepth(router, sessions, 2, "2 on the stack");

            ButtonInteractionEvent previous = click(pagerId("prev"), OWNER);
            assertThat(router.dispatchButton(previous)).isTrue();
            assertOneEdit(previous.getHook());
            assertThat(text(previous.getHook())).contains("1/5").contains("Fake item 1");
            assertDepth(router, sessions, 2, "2 on the stack");

            ButtonInteractionEvent back = click("menu:showcase:nav:back", OWNER);
            assertThat(router.dispatchButton(back)).isTrue();
            assertOneEdit(back.getHook());
            assertThat(text(back.getHook()))
                    .as("back lands on the view it was pushed from, not on home")
                    .contains("Menu showcase");
            assertDepth(router, sessions, 1, "1 on the stack");

            ButtonInteractionEvent presets = click(view(ShowcaseMenu.PRESETS), OWNER);
            assertThat(router.dispatchButton(presets)).isTrue();
            assertOneEdit(presets.getHook());
            assertDepth(router, sessions, 2, "2 on the stack");

            StringSelectInteractionEvent pick =
                    select("menu:showcase:pick_preset", OWNER, BuiltinPresets.MONOCHROME.name());
            assertThat(router.dispatchSelect(pick)).isTrue();
            assertOneEdit(pick.getHook());
            assertThat(accent(pick.getHook()))
                    .as("the picked preset is what the next render uses")
                    .isEqualTo(BuiltinPresets.MONOCHROME.palette().accent());
            assertDepth(router, sessions, 2, "2 on the stack");

            ButtonInteractionEvent modalView = click(view(ShowcaseMenu.MODAL), OWNER);
            assertThat(router.dispatchButton(modalView)).isTrue();
            assertOneEdit(modalView.getHook());
            assertThat(accent(modalView.getHook()))
                    .as("a later view keeps the previewed preset")
                    .isEqualTo(BuiltinPresets.MONOCHROME.palette().accent());
            assertDepth(router, sessions, 3, "3 on the stack");

            // Unacknowledged, because Ack.MODAL means the router defers nothing and the
            // handler answers with the modal itself.
            ButtonInteractionEvent openForm =
                    unacknowledgedClick("menu:showcase:open_form:form", OWNER);
            assertThat(router.dispatchButton(openForm)).isTrue();
            awaitIdle(router);
            verify(openForm, timeout(AWAIT_MS)).replyModal(any());
            assertDepth(router, sessions, 3, "3 on the stack");

            ModalInteractionEvent submit = submit(OWNER, "Ada", "Because it is fun");
            assertThat(router.dispatchModal(submit)).isTrue();
            assertOneEdit(submit.getHook());
            assertThat(text(submit.getHook())).contains("Ada").contains("Because it is fun");
            assertDepth(router, sessions, 3, "3 on the stack");

            // Three entries deep, so three backs walk all the way out.
            ButtonInteractionEvent unwind = null;
            for (int step = 0; step < 3; step++) {
                unwind = click("menu:showcase:nav:back", OWNER);
                assertThat(router.dispatchButton(unwind)).isTrue();
                assertOneEdit(unwind.getHook());
                assertDepth(router, sessions, 2 - step, (2 - step) + " on the stack");
            }
            assertThat(text(unwind.getHook()))
                    .as("the last back leaves the showcase on its home view")
                    .contains("Menu showcase");
        }
        sessions.close();
    }

    @Test
    @DisplayName("answering no returns without deleting anything")
    void cancellingDeletesNothing() {
        ShowcaseMenu menu = new ShowcaseMenu(new PresetRegistry());
        SessionStore sessions = new SessionStore(SessionConfig.defaults());
        try (MenuRouter router = TestRouters.withSessions(sessions)) {
            router.register("showcase", menu);
            ButtonInteractionEvent components = click(view(ShowcaseMenu.COMPONENTS), OWNER);
            assertThat(router.dispatchButton(components)).isTrue();
            assertOneEdit(components.getHook());

            ButtonInteractionEvent delete = click("menu:showcase:delete", OWNER);
            assertThat(router.dispatchButton(delete)).isTrue();
            assertOneEdit(delete.getHook());
            assertThat(text(delete.getHook()))
                    .as("the confirmation asks first")
                    .contains("Remove a fake item")
                    .contains("Remaining items: 25");

            ButtonInteractionEvent no = click("menu:showcase:confirm_no", OWNER);
            assertThat(router.dispatchButton(no)).isTrue();
            assertOneEdit(no.getHook());
            assertThat(text(no.getHook())).as("back on the component view").contains("Fake item 1");
        }
    }

    @Test
    @DisplayName("answering yes removes one item and goes back")
    void confirmingDeletesOneItem() {
        ShowcaseMenu menu = new ShowcaseMenu(new PresetRegistry());
        try (MenuRouter router = TestRouters.with(menu)) {
            ButtonInteractionEvent components = click(view(ShowcaseMenu.COMPONENTS), OWNER);
            assertThat(router.dispatchButton(components)).isTrue();
            assertOneEdit(components.getHook());

            ButtonInteractionEvent delete = click("menu:showcase:delete", OWNER);
            assertThat(router.dispatchButton(delete)).isTrue();
            assertOneEdit(delete.getHook());

            ButtonInteractionEvent yes = click("menu:showcase:confirm_yes", OWNER);
            assertThat(router.dispatchButton(yes)).isTrue();
            assertOneEdit(yes.getHook());
            assertThat(text(yes.getHook()))
                    .as("back on the component view")
                    .contains("Fake item 1");

            ButtonInteractionEvent deleteAgain = click("menu:showcase:delete", OWNER);
            assertThat(router.dispatchButton(deleteAgain)).isTrue();
            assertOneEdit(deleteAgain.getHook());
            assertThat(text(deleteAgain.getHook()))
                    .as("the confirmation counts what is left")
                    .contains("Remaining items: 24");

            // Page one holds items 1 to 5, so the removal is only visible at the end of
            // the list: four clicks to reach the last page of twenty-four items.
            ButtonInteractionEvent lastPage = null;
            for (int page = 0; page < 4; page++) {
                lastPage = click(pagerId("next"), OWNER);
                assertThat(router.dispatchButton(lastPage)).isTrue();
                assertOneEdit(lastPage.getHook());
            }
            assertThat(text(lastPage.getHook()))
                    .as("the last item is gone from the list itself")
                    .contains("Fake item 24")
                    .doesNotContain("Fake item 25");
        }
    }

    @Test
    @DisplayName("another user pressing the same buttons is refused, ephemerally")
    void aStrangerIsRefused() {
        ShowcaseMenu menu = new ShowcaseMenu(new PresetRegistry());
        try (MenuRouter router = TestRouters.with(menu)) {
            ButtonInteractionEvent foreign = click(view(ShowcaseMenu.COMPONENTS), STRANGER);
            assertThat(router.dispatchButton(foreign)).isTrue();
            awaitIdle(router);

            assertThat(reply(foreign.getHook())).isEqualTo(english(MessageKeys.ERROR_NOT_OWNER));
            verify(foreign.getHook(), never())
                    .editOriginalComponents(any(MessageTopLevelComponent[].class));
        }
    }

    @Test
    @DisplayName("with no session left, Back says it expired and lands on home")
    void anExpiredSessionSaysSoAndShowsHome() {
        ShowcaseMenu menu = new ShowcaseMenu(new PresetRegistry());
        SessionStore sessions = new SessionStore(SessionConfig.defaults());
        try (MenuRouter router = TestRouters.withSessions(sessions)) {
            router.register("showcase", menu);
            // No session for this message, which is exactly what an expired one looks like.
            ButtonInteractionEvent back = click("menu:showcase:nav:back", OWNER);
            assertThat(router.dispatchButton(back)).isTrue();

            assertThat(reply(back.getHook())).isEqualTo(english(MessageKeys.NAV_EXPIRED));
            assertOneEdit(back.getHook());
            assertThat(text(back.getHook())).contains("Menu showcase");
        }
        sessions.close();
    }

    // ------------------------------------------------------------- helpers

    /** The page button for the pager the component view draws. */
    private static String pagerId(String direction) {
        return "menu:showcase:page:" + direction + ":items:components";
    }

    /**
     * The id of a navigation to a view of the showcase itself.
     *
     * <p>Built rather than written, because the whole point of the change is that the id
     * carries the view and the router reads it back.
     */
    private static String view(String viewAction) {
        return NavigationAction.buttonId(
                "showcase", NavigationMode.PUSH, new NavEntry("showcase", viewAction, List.of()));
    }

    private static ButtonInteractionEvent click(String componentId, long user) {
        return JdaMocks.button(componentId, true, MESSAGE, user);
    }

    /** An interaction the router has left open, which is what opening a modal needs. */
    private static ButtonInteractionEvent unacknowledgedClick(String componentId, long user) {
        return JdaMocks.button(componentId, false, MESSAGE, user);
    }

    private static StringSelectInteractionEvent select(
            String componentId, long user, String... values) {
        return JdaMocks.select(componentId, true, MESSAGE, user, values);
    }

    private static ModalInteractionEvent submit(long user, String name, String reason) {
        // Built before the stubbing starts: Mockito cannot record a stub while another one
        // is being written.
        List<ModalMapping> values = List.of(mapping("name", name), mapping("reason", reason));
        ModalInteractionEvent event =
                JdaMocks.modal("menu:showcase:submit_form:form", true, MESSAGE, user);
        when(event.getValues()).thenReturn(values);
        return event;
    }

    private static ModalMapping mapping(String id, String value) {
        ModalMapping mapping = mock(ModalMapping.class);
        when(mapping.getCustomId()).thenReturn(id);
        when(mapping.getAsString()).thenReturn(value);
        return mapping;
    }

    /** Exactly one edit, which is what a single handled interaction looks like. */
    private static void assertOneEdit(InteractionHook hook) {
        verify(hook, timeout(AWAIT_MS))
                .editOriginalComponents(any(MessageTopLevelComponent[].class));
    }

    /**
     * The session history depth once a click has fully finished.
     *
     * <p>Waits for the router to release the message first, because the push happens after
     * the edit and a depth read between the two would be a race rather than a result.
     *
     * @param because what this depth proves, so a failure says why it mattered
     */
    private static void assertDepth(
            MenuRouter router, SessionStore sessions, int expected, String because) {
        awaitIdle(router);
        assertThat(sessions.find(MESSAGE).map(Session::depth).orElse(0))
                .as(because)
                .isEqualTo(expected);
    }

    private static void awaitIdle(MenuRouter router) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_MS);
        while (router.inFlight() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(router.inFlight()).as("no interaction is still claimed").isZero();
    }

    private static String english(String key) {
        return Messages.standard().get(Locale.ENGLISH, key);
    }

    private static String reply(InteractionHook hook) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(hook, timeout(AWAIT_MS)).sendMessage(captor.capture());
        return captor.getValue();
    }

    private static int accent(InteractionHook hook) {
        return container(hook).getAccentColorRaw();
    }

    /** The container the hook was asked to write, from the captured call. */
    private static Container container(InteractionHook hook) {
        ArgumentCaptor<MessageTopLevelComponent[]> captor =
                ArgumentCaptor.forClass(MessageTopLevelComponent[].class);
        verify(hook, timeout(AWAIT_MS)).editOriginalComponents(captor.capture());
        for (MessageTopLevelComponent component : captor.getValue()) {
            if (component instanceof Container written) {
                return written;
            }
        }
        throw new AssertionError("the message was not edited with a container");
    }

    /** The rendered view as text: every text line and every button label. */
    private static String text(InteractionHook hook) {
        StringBuilder out = new StringBuilder();
        for (var child : container(hook).getComponents()) {
            if (child instanceof TextDisplay display) {
                out.append(display.getContent()).append('\n');
            } else if (child instanceof ActionRow row) {
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
