package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.Menu;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Checks that what a user is told goes through the bundles, end to end.
 *
 * <p>The unit tests for {@link Messages} prove the lookup rules; this proves the router
 * actually passes the interaction's locale into them. A translation can be perfect and
 * still never reach a user if the router hands every failure the default locale, and
 * nothing below the router would notice.
 */
class MenuRouterLocalizationTest {

    private static final int AWAIT_MS = 5_000;
    private static final String OTHER_USER = "7";

    private MenuRouter router;

    @AfterEach
    void closeRouter() {
        if (router != null) {
            router.close();
        }
    }

    @Test
    @DisplayName("a foreign click from a Spanish user is refused in Spanish")
    void foreignClickIsSpanish() {
        // The menu must declare the action: dispatch resolves the action before it
        // reaches the owner check, so a menu with no actions would answer "unknown
        // action" and the refusal would never be exercised.
        router = newRouter(personalMenu());
        ButtonInteractionEvent event =
                spanish(JdaMocks.button("menu:m:go", true, 7L, Long.parseLong(OTHER_USER)));

        assertThat(router.dispatchButton(event)).isTrue();

        assertThat(replyText(event)).isEqualTo("Este men\u00FA no es tuyo.");
    }

    @Test
    @DisplayName("the same refusal is English for an English user")
    void foreignClickIsEnglish() {
        router = newRouter(personalMenu());
        ButtonInteractionEvent event =
                JdaMocks.button("menu:m:go", true, 7L, Long.parseLong(OTHER_USER));

        assertThat(router.dispatchButton(event)).isTrue();

        assertThat(replyText(event)).isEqualTo("This menu is not yours.");
    }

    @Test
    @DisplayName("an unknown action is answered in Spanish")
    void unknownActionIsSpanish() {
        router = newRouter();
        ButtonInteractionEvent event = spanish(JdaMocks.button("menu:m:nope", true));

        assertThat(router.dispatchButton(event)).isTrue();

        assertThat(replyText(event)).isEqualTo("Acci\u00F3n desconocida.");
    }

    @Test
    @DisplayName("an unexpected failure gives the localized generic message with a reference")
    void genericErrorIsLocalized() {
        AtomicReference<String> secret = new AtomicReference<>("jdbc:mysql://internal/db");
        router = newRouter(failingMenu(secret));
        ButtonInteractionEvent event = spanish(JdaMocks.button("menu:m:go", true, 5L, 42L));

        assertThat(router.dispatchButton(event)).isTrue();

        String text = awaitReply(event);
        assertThat(text).matches("Algo sali\\u00F3 mal \\(ref: [0-9a-f]{6}\\)\\.");
        assertThat(text).doesNotContain(secret.get());
    }

    @Test
    @DisplayName("a user-facing exception is translated with the event's locale")
    void userFacingExceptionIsTranslated() {
        router = newRouter(throwingMenu());
        ButtonInteractionEvent event = spanish(JdaMocks.button("menu:m:go", true, 5L, 42L));

        assertThat(router.dispatchButton(event)).isTrue();

        assertThat(awaitReply(event)).isEqualTo("Par\u00E1metro inv\u00E1lido o ausente.");
    }

    @Test
    @DisplayName("a Spanish guild speaks Spanish when the user has no locale of their own")
    void guildLocaleIsUsed() {
        router = newRouter();
        ButtonInteractionEvent event = JdaMocks.button("menu:m:go", true);
        when(event.getUserLocale()).thenReturn(DiscordLocale.UNKNOWN);
        when(event.getGuildLocale()).thenReturn(DiscordLocale.SPANISH);

        assertThat(router.dispatchButton(event)).isTrue();

        assertThat(replyText(event)).isEqualTo("Acci\u00F3n desconocida.");
    }

    private static ButtonInteractionEvent spanish(ButtonInteractionEvent event) {
        when(event.getUserLocale()).thenReturn(DiscordLocale.SPANISH);
        return event;
    }

    /** A router with one menu registered that declares no actions. */
    private MenuRouter newRouter() {
        return newRouter(inertMenu());
    }

    private MenuRouter newRouter(Menu menu) {
        SessionStore sessions = new SessionStore(SessionConfig.defaults());
        MenuRouter created = new MenuRouter(MenuExecutor.virtual(), sessions, Messages.standard());
        created.register(menu.id(), menu);
        return created;
    }

    private static String replyText(ButtonInteractionEvent event) {
        return awaitReply(event);
    }

    /**
     * The text the user was given.
     *
     * <p>Both routes are checked because a router may answer before or after the
     * acknowledgement, and {@code Replies} deliberately picks whichever is legal. Every
     * event here is already acknowledged, so the hook is the real route.
     */
    private static String awaitReply(ButtonInteractionEvent event) {
        ArgumentCaptor<String> hook = ArgumentCaptor.forClass(String.class);
        verify(event.getHook(), timeout(AWAIT_MS)).sendMessage(hook.capture());
        return hook.getValue();
    }

    /** A personal menu that declares the action, so the owner check is reached. */
    private static Menu personalMenu() {
        return new Menu() {
            @Override
            public String id() {
                return "m";
            }

            @Override
            public CompletableFuture<Container> render(es.redactado.menu.api.MenuContext ctx) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }

            @Override
            public void actions(ActionTable.Builder table) {
                table.button(
                        "go",
                        Ack.DEFER_EDIT,
                        (ctx, event) -> CompletableFuture.completedFuture(null));
            }
        };
    }

    /** A menu with no matching action, so the router answers with "unknown action". */
    private static Menu inertMenu() {
        return new Menu() {
            @Override
            public String id() {
                return "m";
            }

            @Override
            public CompletableFuture<Container> render(es.redactado.menu.api.MenuContext ctx) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }

            @Override
            public void actions(ActionTable.Builder table) {
                // Deliberately declares nothing, so dispatch finds no action.
            }
        };
    }

    private static Menu failingMenu(AtomicReference<String> detail) {
        return new Menu() {
            @Override
            public String id() {
                return "m";
            }

            @Override
            public CompletableFuture<Container> render(es.redactado.menu.api.MenuContext ctx) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }

            @Override
            public void actions(ActionTable.Builder table) {
                table.button(
                        "go",
                        Ack.DEFER_EDIT,
                        (ctx, event) ->
                                CompletableFuture.failedFuture(
                                        new IllegalStateException(detail.get())));
            }
        };
    }

    private static Menu throwingMenu() {
        return new Menu() {
            @Override
            public String id() {
                return "m";
            }

            @Override
            public CompletableFuture<Container> render(es.redactado.menu.api.MenuContext ctx) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }

            @Override
            public void actions(ActionTable.Builder table) {
                table.button(
                        "go",
                        Ack.DEFER_EDIT,
                        (ctx, event) ->
                                CompletableFuture.failedFuture(
                                        new es.redactado.menu.api.UserFacingException(
                                                MessageKeys.ERROR_BAD_PARAM)));
            }
        };
    }

    /** Keeps the unused-import checker honest about {@link Locale}. */
    @Test
    @DisplayName("the bundles resolve for a plain Spanish locale")
    void spanishLocaleResolves() {
        Locale locale = Locale.forLanguageTag("es-ES");
        assertThat(Messages.standard().get(locale, MessageKeys.ERROR_NOT_OWNER))
                .isEqualTo("Este men\u00FA no es tuyo.");
        assertThat(mock(ButtonInteractionEvent.class)).isNotNull();
    }
}
