package es.redactado.menu.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Session;
import es.redactado.menu.api.UserFacingException;
import es.redactado.menu.core.ComponentId;
import es.redactado.menu.core.JdaMocks;
import es.redactado.menu.core.MenuRouter;
import es.redactado.menu.core.Messages;
import es.redactado.menu.core.TestRouters;
import es.redactado.menu.examples.ProfileExampleMenu.Profile;
import es.redactado.menu.preset.BuiltinPresets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The profile example driven through the router, one interaction at a time.
 *
 * <p>Every step is a separate event with its own mock, because that is what a real
 * interaction is: the previous button press is over and its acknowledgement is spent by the
 * time the next one arrives. A test that reused one event object would pass for reasons a real
 * user never gets.
 *
 * <p>The service blocks for twenty milliseconds and the cache sits in front of it, so these
 * tests also assert the thing the cache is for: two renders of one profile cost one call, and
 * a write costs exactly one more.
 */
class ProfileExampleEndToEndTest {

    private static final long MESSAGE = 900L;

    /** The fixture's clicker, and so the message owner a press has to match. */
    private static final long USER = 42L;

    /** Somebody else's message, for the owner check. */
    private static final long STRANGER = 7L;

    @Test
    @DisplayName("two renders of one profile cost one load, because the cache sits in front")
    void rendersAreCached() {
        FakeProfileService service = new FakeProfileService(20);
        try (ProfileExampleMenu menu = ProfileExampleMenu.using(service)) {
            menu.render(renderContext("home")).join();
            menu.render(renderContext("home")).join();
            menu.render(renderContext("home")).join();

            assertThat(service.loadCalls())
                    .as(
                            "three renders of the same key, one call: a cache that missed here"
                                    + " would cost a database round trip per keystroke of a redraw")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("the home view shows the profile the service returned")
    void homeShowsTheProfile() {
        try (ProfileExampleMenu menu = ProfileExampleMenu.using(new FakeProfileService(0))) {
            Container container = menu.render(renderContext("home")).join();

            assertThat(textOf(container))
                    .contains("Profile")
                    .contains("1990-01-01")
                    .contains("Links: 6");
            assertThat(buttonsOf(container))
                    .as(
                            "five role buttons on the first page, three pager buttons, and the"
                                    + " menu's own three")
                    .hasSize(11);
        }
    }

    @Test
    @DisplayName("a role row opens the role view with its id, read back with requireLong")
    void roleViewReadsItsParameter() {
        FakeProfileService service = new FakeProfileService(0);
        try (ProfileExampleMenu menu = ProfileExampleMenu.using(service);
                MenuRouter router = TestRouters.with(menu)) {

            Container home = menu.render(renderContext("home")).join();
            String navId = navigationIdOf(home, ProfileExampleMenu.ROLE, "1001");
            ButtonInteractionEvent event = JdaMocks.button(navId, true, MESSAGE, USER);
            assertThat(router.dispatchButton(event)).isTrue();

            assertThat(editedText(event.getHook()))
                    .as("the role whose id was in the component id")
                    .contains("Owner")
                    .contains("Everything");
        }
    }

    @Test
    @DisplayName("a role id that parses but names nothing is the unknown-view sentence")
    void unknownRoleIsRefused() {
        // A malformed id is the context's business and is already proved there, by
        // MenuRouterDispatchTest's requireLong cases; what this menu owns is what it does
        // with a well-formed id that matches no role.
        try (ProfileExampleMenu menu = ProfileExampleMenu.using(new FakeProfileService(0))) {
            assertThat(
                            catchThrowable(
                                    () ->
                                            menu.render(
                                                            renderContext(
                                                                    ProfileExampleMenu.ROLE,
                                                                    List.of("999999")))
                                                    .join()))
                    .as("an id a client could have edited must not render a blank view")
                    .hasRootCauseInstanceOf(UserFacingException.class)
                    .rootCause()
                    .hasMessageContaining("menu.error.unknown_view");
        }
    }

    @Test
    @DisplayName("the birth form opens, and its submission writes and invalidates the cache")
    void birthDateRoundTrip() {
        FakeProfileService service = new FakeProfileService(20);
        try (ProfileExampleMenu menu = ProfileExampleMenu.using(service);
                MenuRouter router = TestRouters.with(menu)) {

            Container home = menu.render(renderContext("home")).join();
            ButtonInteractionEvent open =
                    JdaMocks.button(
                            actionId(home, ProfileExampleMenu.ASK_BIRTH), false, MESSAGE, USER);
            assertThat(router.dispatchButton(open)).isTrue();

            verify(open, timeout(5_000)).replyModal(any(Modal.class));
            verify(open, never()).deferEdit();

            ModalInteractionEvent submit =
                    JdaMocks.modal(
                            ComponentId.encode("profile", ProfileExampleMenu.SAVE_BIRTH), true);
            // Built before the stubbing starts: Mockito cannot record a stub while another
            // stubbing is in progress.
            List<ModalMapping> values = List.of(mapping("birth", " 1999-12-31 "));
            when(submit.getValues()).thenReturn(values);

            assertThat(router.dispatchModal(submit)).isTrue();

            // Waiting for the redraw rather than for the write: the load count only settles
            // once the refresh has run, and asserting before that would be asserting that the
            // chain had not got to it yet.
            verify(submit.getHook(), timeout(5_000))
                    .editOriginalComponents(any(MessageTopLevelComponent[].class));

            assertThat(service.birthWrites()).as("one write, off the event thread").isEqualTo(1);
            assertThat(service.loadCalls())
                    .as(
                            "the render before the write loaded once; invalidating the key means"
                                    + " the redraw after it reloads, which is the whole point of"
                                    + " invalidateAfter")
                    .isEqualTo(2);
            assertThat(service.stored(USER).birthDate())
                    .as("stored as typed apart from the surrounding spaces")
                    .isEqualTo("1999-12-31");
        }
    }

    @Test
    @DisplayName("the links view pages, and its own form adds a link")
    void linksViewPagesAndWrites() {
        FakeProfileService service = new FakeProfileService(0);
        try (ProfileExampleMenu menu = ProfileExampleMenu.using(service)) {
            Container links = menu.render(renderContext(ProfileExampleMenu.LINKS)).join();

            assertThat(textOf(links))
                    .as("the first page of five, with the sixth waiting")
                    .contains("Site: https://example.com")
                    .contains("Privacy: https://example.com/privacy")
                    .doesNotContain("Terms:");
        }

        try (ProfileExampleMenu menu = ProfileExampleMenu.using(service);
                MenuRouter router = TestRouters.with(menu)) {
            Container links = menu.render(renderContext(ProfileExampleMenu.LINKS)).join();
            ButtonInteractionEvent add =
                    JdaMocks.button(
                            actionId(links, ProfileExampleMenu.ASK_LINK), false, MESSAGE, USER);
            assertThat(router.dispatchButton(add)).isTrue();
            verify(add, timeout(5_000)).replyModal(any(Modal.class));

            ModalInteractionEvent submit =
                    JdaMocks.modal(
                            ComponentId.encode("profile", ProfileExampleMenu.SAVE_LINK), true);
            List<ModalMapping> values =
                    List.of(mapping("label", "Repo"), mapping("url", "https://git.example"));
            when(submit.getValues()).thenReturn(values);

            assertThat(router.dispatchModal(submit)).isTrue();

            // Waiting for the effect rather than the counter: the counter is incremented at
            // the start of the blocking call, so it says the call began, not that it finished.
            await(
                    () -> service.stored(USER) != null && service.stored(USER).links().size() == 7,
                    "the link write ran off the event thread");
            assertThat(service.linkWrites()).isEqualTo(1);
            assertThat(service.stored(USER).links())
                    .as("seven links now, the new one last")
                    .hasSize(7)
                    .last()
                    .isEqualTo(new Profile.Link("Repo", "https://git.example"));
        }
    }

    @Test
    @DisplayName("removing asks first, and the confirmation is what removes")
    void removalIsConfirmed() {
        FakeProfileService service = new FakeProfileService(0);
        try (ProfileExampleMenu menu = ProfileExampleMenu.using(service);
                MenuRouter router = TestRouters.with(menu)) {

            Container home = menu.render(renderContext("home")).join();
            ButtonInteractionEvent ask =
                    JdaMocks.button(
                            actionId(home, ProfileExampleMenu.ASK_REMOVE), true, MESSAGE, USER);
            assertThat(router.dispatchButton(ask)).isTrue();

            assertThat(editedText(ask.getHook()))
                    .as("the button asked rather than acted")
                    .contains("Remove profile")
                    .contains("cannot be undone");
            assertThat(service.removals()).as("nothing was removed yet").isZero();

            Container confirm =
                    menu.render(
                                    renderContext(
                                            ProfileExampleMenu.CONFIRM_REMOVE,
                                            List.of(),
                                            "profile",
                                            List.of()))
                            .join();
            ButtonInteractionEvent remove =
                    JdaMocks.button(
                            actionId(confirm, ProfileExampleMenu.DO_REMOVE), true, MESSAGE, USER);
            assertThat(router.dispatchButton(remove)).isTrue();

            await(() -> service.stored(USER) == null, "the removal ran off the event thread");
            assertThat(service.removals()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Back from a role returns to home, because currentView says what is on screen")
    void backFromARoleReturnsHome() {
        try (ProfileExampleMenu menu = ProfileExampleMenu.using(new FakeProfileService(0))) {
            MenuRouter router = TestRouters.with(menu);
            try (router) {
                Container home = menu.render(renderContext("home")).join();
                ButtonInteractionEvent push =
                        JdaMocks.button(
                                navigationIdOf(home, ProfileExampleMenu.ROLE, "1001"),
                                true,
                                MESSAGE,
                                USER);
                assertThat(router.dispatchButton(push)).isTrue();
                editedText(push.getHook());

                ButtonInteractionEvent back =
                        JdaMocks.button(
                                backIdOf(
                                        menu.render(
                                                        renderContext(
                                                                ProfileExampleMenu.ROLE,
                                                                List.of("1001")))
                                                .join()),
                                true,
                                MESSAGE,
                                USER);
                assertThat(router.dispatchButton(back)).isTrue();

                assertThat(editedText(back.getHook()))
                        .as(
                                "without the currentView override this would try to render an"
                                    + " action named nav, and the user would be told that view is"
                                    + " not available")
                        .contains("Profile")
                        .contains("Birth date")
                        .doesNotContain("Permissions")
                        .doesNotContain("Can mute");
            }
        }
    }

    @Test
    @DisplayName("a stranger cannot press a personal menu, as for any menu")
    void strangerIsDenied() {
        FakeProfileService service = new FakeProfileService(0);
        try (ProfileExampleMenu menu = ProfileExampleMenu.using(service);
                MenuRouter router = TestRouters.with(menu)) {

            Container home = menu.render(renderContext("home")).join();
            // The message belongs to OWNER; the press comes from the fixture's fixed clicker.
            ButtonInteractionEvent event =
                    JdaMocks.button(
                            actionId(home, ProfileExampleMenu.ASK_REMOVE),
                            false,
                            MESSAGE,
                            STRANGER);

            assertThat(router.dispatchButton(event)).isTrue();

            ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
            verify(event, timeout(5_000)).reply(reply.capture());
            assertThat(reply.getValue()).isEqualTo("This menu is not yours.");
            assertThat(service.removals()).as("the handler never ran").isZero();
        }
    }

    @Test
    @DisplayName("a view this menu does not have is one localized sentence")
    void unknownViewIsLocalized() {
        try (ProfileExampleMenu menu = ProfileExampleMenu.using(new FakeProfileService(0))) {
            assertThat(catchThrowable(() -> menu.render(renderContext("nope")).join()))
                    .hasRootCauseInstanceOf(UserFacingException.class)
                    .rootCause()
                    .hasMessageContaining("menu.error.unknown_view");
        }
    }

    // ------------------------------------------------------------------ helpers

    /** A context with no session, as an opening interaction has. */
    private static MenuContext renderContext(String view) {
        return renderContext(view, List.of());
    }

    private static MenuContext renderContext(String view, List<String> params) {
        return renderContext(view, params, "profile", params);
    }

    /**
     * A render context that resolves keys and a session.
     *
     * <p>The user id is {@value #USER}, which is also what the fixture's clicker reports, so
     * a press and the render it refreshes agree about whose profile this is.
     */
    private static MenuContext renderContext(
            String view, List<String> params, String menuId, List<String> reported) {
        MenuContext ctx = mock(MenuContext.class, CALLS_REAL_METHODS);
        Session session = new Session();
        when(ctx.menuId()).thenReturn(menuId);
        when(ctx.action()).thenReturn(view);
        when(ctx.params()).thenReturn(reported);
        when(ctx.preset()).thenReturn(BuiltinPresets.DEFAULT);
        when(ctx.locale()).thenReturn(Locale.ENGLISH);
        when(ctx.userId()).thenReturn(Long.toString(USER));
        when(ctx.session()).thenReturn(session);
        when(ctx.findSession()).thenReturn(java.util.Optional.of(session));
        when(ctx.requireLong(anyInt()))
                .thenAnswer(call -> Long.parseLong(params.get(call.<Integer>getArgument(0))));
        when(ctx.t(anyString(), any(Object[].class)))
                .thenAnswer(
                        call -> {
                            Object[] all = call.getArguments();
                            Object[] args = new Object[all.length - 1];
                            System.arraycopy(all, 1, args, 0, args.length);
                            return Messages.standard().get(Locale.ENGLISH, (String) all[0], args);
                        });
        return ctx;
    }

    /** The component id of the button that runs an action. */
    private static String actionId(Container container, String action) {
        for (Button button : buttonsOf(container)) {
            ComponentId id = ComponentId.decode(button.getCustomId()).orElse(null);
            if (id != null && action.equals(id.action())) {
                return button.getCustomId();
            }
        }
        throw new AssertionError("no button for action " + action);
    }

    /** The id of a navigation button in the container that targets a view. */
    private static String navigationIdOf(Container container, String view, String... params) {
        for (Button button : buttonsOf(container)) {
            ComponentId id = ComponentId.decode(button.getCustomId()).orElse(null);
            if (id != null
                    && "nav".equals(id.action())
                    && id.params().size() > 2
                    && view.equals(id.params().get(2))) {
                return button.getCustomId();
            }
        }
        throw new AssertionError("no navigation button to " + view);
    }

    /** The id of the Back button, which a nav action encodes as a mode and no target. */
    private static String backIdOf(Container container) {
        for (Button button : buttonsOf(container)) {
            ComponentId id = ComponentId.decode(button.getCustomId()).orElse(null);
            if (id != null && "nav".equals(id.action()) && id.params().contains("back")) {
                return button.getCustomId();
            }
        }
        throw new AssertionError("the view rendered no back button");
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

    /**
     * Waits for work the router hands to an executor.
     *
     * <p>The handler chain is asynchronous by design, so asserting straight after a dispatch
     * would be asserting that a blocking service call happened to be scheduled already.
     */
    private static void await(java.util.function.BooleanSupplier done, String what) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!done.getAsBoolean() && System.nanoTime() < deadline) {
            java.lang.Thread.onSpinWait();
        }
        assertThat(done.getAsBoolean()).as(what).isTrue();
    }

    /** One submitted modal field. */
    private static ModalMapping mapping(String id, String value) {
        ModalMapping mapping = mock(ModalMapping.class);
        when(mapping.getCustomId()).thenReturn(id);
        when(mapping.getAsString()).thenReturn(value);
        return mapping;
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
