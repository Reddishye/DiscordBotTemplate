package es.redactado.menu.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.redactado.menu.core.MessageKeys;
import es.redactado.menu.core.Messages;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two kinds of message a view can hold.
 *
 * <p>Both resolve through the context, so a test with a stubbed {@code t} covers the whole
 * contract: what the author wrote and what the reader ends up seeing.
 */
class MsgTest {

    @Test
    @DisplayName("a literal is the same for everyone")
    void literalIsFixed() {
        Msg msg = Msg.literal("https://example.com");

        assertThat(msg.get(context(Locale.ENGLISH))).isEqualTo("https://example.com");
        assertThat(msg.get(context(Locale.forLanguageTag("es"))))
                .as("a literal is not translated, which is why it suits content not chrome")
                .isEqualTo("https://example.com");
    }

    @Test
    @DisplayName("a key resolves in English")
    void keyResolvesInEnglish() {
        assertThat(Msg.key(MessageKeys.NAV_BACK).get(context(Locale.ENGLISH))).isEqualTo("Back");
    }

    @Test
    @DisplayName("a key resolves in Spanish")
    void keyResolvesInSpanish() {
        assertThat(Msg.key(MessageKeys.NAV_BACK).get(context(Locale.forLanguageTag("es"))))
                .as("the same key, the reader's language")
                .isEqualTo("Atr\u00E1s");
    }

    @Test
    @DisplayName("arguments are substituted")
    void argumentsAreSubstituted() {
        Msg msg = Msg.key(MessageKeys.ERROR_GENERIC, "abc123");

        assertThat(msg.get(context(Locale.ENGLISH)))
                .isEqualTo("Something went wrong (ref: abc123).");
    }

    @Test
    @DisplayName("a key with no arguments still resolves")
    void keyWithoutArguments() {
        assertThat(Msg.key(MessageKeys.ERROR_BUSY).get(context(Locale.ENGLISH)))
                .isEqualTo("The bot is busy. Try again.");
    }

    @Test
    @DisplayName("an unknown key falls back to itself, loudly")
    void unknownKeyIsVisible() {
        assertThat(Msg.key("menu.nothing.here").get(context(Locale.ENGLISH)))
                .as("a missing translation shows the key rather than a gap")
                .isEqualTo("menu.nothing.here");
    }

    @Test
    @DisplayName("null or empty text is refused at construction")
    void badInputIsRefused() {
        assertThatThrownBy(() -> Msg.literal(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
        assertThatThrownBy(() -> Msg.key(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
        assertThatThrownBy(() -> Msg.key(null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** A context whose {@code t} resolves against the real bundles. */
    private static MenuContext context(Locale locale) {
        MenuContext ctx = mock(MenuContext.class);
        when(ctx.locale()).thenReturn(locale);
        when(ctx.t(any(String.class), any(Object[].class)))
                .thenAnswer(
                        call -> {
                            Object[] all = call.getArguments();
                            Object[] args = new Object[all.length - 1];
                            System.arraycopy(all, 1, args, 0, args.length);
                            return Messages.standard().get(locale, (String) all[0], args);
                        });
        return ctx;
    }
}
