package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Covers locale selection and the substitution rules.
 *
 * <p>Note the package: JDA 6.5.0 declares {@code DiscordLocale} in
 * {@code net.dv8tion.jda.api.interactions}, not in a {@code locales} subpackage, and
 * only {@link Interaction#getUserLocale()} exposes a user's locale. There is no
 * {@code User.getLocale()}.
 */
class LocalesAndMessagesTest {

    private final Messages messages = Messages.standard();

    private static Locale spanish() {
        return Locale.forLanguageTag("es-ES");
    }

    @Test
    @DisplayName("a Spanish user gets Spanish")
    void spanishUser() {
        assertThat(Locales.resolve(DiscordLocale.SPANISH, DiscordLocale.UNKNOWN))
                .isEqualTo(spanish());
    }

    @Test
    @DisplayName("a Latin American Spanish user gets Spanish")
    void spanishLatam() {
        assertThat(Locales.resolve(DiscordLocale.SPANISH_LATAM, DiscordLocale.UNKNOWN))
                .as("es-419 is a different tag but shares the messages_es bundle")
                .isEqualTo(Locale.forLanguageTag("es-419"));
    }

    @Test
    @DisplayName("an untranslated language falls back to English")
    void untranslatedFallsBack() {
        assertThat(messages.get(Locale.forLanguageTag("fr"), MessageKeys.ERROR_BUSY))
                .isEqualTo("The bot is busy. Try again.");
    }

    @Test
    @DisplayName("an unknown language falls back to English")
    void unknownFallsBack() {
        assertThat(messages.get(Locale.forLanguageTag("zz-ZZ"), MessageKeys.ERROR_BUSY))
                .isEqualTo("The bot is busy. Try again.");
    }

    @Test
    @DisplayName("an unknown user with a Spanish guild gets Spanish")
    void guildDecidesWhenUserIsUnknown() {
        assertThat(Locales.resolve(DiscordLocale.UNKNOWN, DiscordLocale.SPANISH))
                .isEqualTo(spanish());
    }

    @Test
    @DisplayName("both unknown gives English")
    void bothUnknownGivesEnglish() {
        assertThat(Locales.resolve(DiscordLocale.UNKNOWN, DiscordLocale.UNKNOWN))
                .isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("nulls are treated as unknown rather than throwing")
    void nullsAreUnknown() {
        assertThat(Locales.resolve(null, null)).isEqualTo(Locale.ENGLISH);
        assertThat(Locales.resolve(null, DiscordLocale.SPANISH)).isEqualTo(spanish());
        assertThat(Locales.resolve(DiscordLocale.SPANISH, null)).isEqualTo(spanish());
    }

    @Test
    @DisplayName("a user's own locale beats the guild's")
    void userBeatsGuild() {
        assertThat(Locales.resolve(DiscordLocale.SPANISH, DiscordLocale.GERMAN))
                .isEqualTo(spanish());
    }

    @Test
    @DisplayName("the JVM default locale cannot change the English fallback")
    void platformLocaleDoesNotLeak() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);

            assertThat(Locales.resolve(DiscordLocale.UNKNOWN, DiscordLocale.UNKNOWN))
                    .as("selection must not consult the platform")
                    .isEqualTo(Locale.ENGLISH);
            assertThat(messages.get(Locale.ENGLISH, MessageKeys.ERROR_BUSY))
                    .as("the bundle lookup must not consult the platform either")
                    .isEqualTo("The bot is busy. Try again.");
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    @DisplayName("one argument is substituted")
    void oneArgument() {
        String text = messages.get(Locale.ENGLISH, MessageKeys.ERROR_GENERIC, "a1b2c3");

        assertThat(text).isEqualTo("Something went wrong (ref: a1b2c3).");
    }

    @Test
    @DisplayName("two arguments are substituted in order")
    void twoArguments() {
        String text = Messages.substitute("{0} and {1}", "first", "second");

        assertThat(text).isEqualTo("first and second");
    }

    @Test
    @DisplayName("the same placeholder may be used twice")
    void repeatedPlaceholder() {
        assertThat(Messages.substitute("{0} then {0}", "x")).isEqualTo("x then x");
    }

    @Test
    @DisplayName("a placeholder with no matching argument is left exactly as it is")
    void missingArgumentIsLeftAlone() {
        // Left visible rather than rendered as empty or as the number: a user seeing
        // "{1}" in a message has no way to tell it from content, so leaving it tells a
        // translator something is wrong.
        assertThat(Messages.substitute("value {0} of {1}", "only")).isEqualTo("value only of {1}");
        assertThat(Messages.substitute("nothing {0}", (Object[]) new Object[0]))
                .isEqualTo("nothing {0}");
    }

    @Test
    @DisplayName("braces that hold something other than a number are left alone")
    void nonNumericBracesAreLeftAlone() {
        assertThat(Messages.substitute("{name} stays", "ignored")).isEqualTo("{name} stays");
        assertThat(Messages.substitute("{ 0 } stays", "ignored")).isEqualTo("{ 0 } stays");
        assertThat(Messages.substitute("{} stays", "ignored")).isEqualTo("{} stays");
        assertThat(Messages.substitute("{-1} stays", "ignored")).isEqualTo("{-1} stays");
    }

    @Test
    @DisplayName("an unclosed brace is left alone")
    void unclosedBraceIsLeftAlone() {
        assertThat(Messages.substitute("trailing {0", "x")).isEqualTo("trailing {0");
    }

    @Test
    @DisplayName("a message with no arguments returns the stored instance every time")
    void noArgumentsReturnsSameInstance() {
        String first = messages.get(Locale.ENGLISH, MessageKeys.ERROR_BUSY);
        String second = messages.get(Locale.ENGLISH, MessageKeys.ERROR_BUSY);

        assertThat(first).isSameAs(second);
        assertThat(first).isEqualTo("The bot is busy. Try again.");
    }

    @Test
    @DisplayName("a missing key returns the key itself")
    void missingKeyReturnsTheKey() {
        assertThat(messages.get(Locale.ENGLISH, "menu.error.nope")).isEqualTo("menu.error.nope");
    }

    @Test
    @DisplayName("a missing key is tolerated in Spanish too")
    void missingKeyInSpanish() {
        assertThat(messages.get(spanish(), "menu.error.nope")).isEqualTo("menu.error.nope");
    }

    @Test
    @DisplayName("Spanish text is returned for a Spanish locale")
    void spanishText() {
        assertThat(messages.get(spanish(), MessageKeys.ERROR_NOT_OWNER))
                .isEqualTo("Este men\u00FA no es tuyo.");
    }

    @Test
    @DisplayName("the key set is stable across repeated lookups")
    void repeatedLookupsAreStable() {
        for (int i = 0; i < 3; i++) {
            assertThat(messages.get(spanish(), MessageKeys.ERROR_BAD_PARAM))
                    .isEqualTo("Par\u00E1metro inv\u00E1lido o ausente.");
            assertThat(messages.get(Locale.forLanguageTag("fr"), MessageKeys.ERROR_BUSY))
                    .isEqualTo("The bot is busy. Try again.");
        }
    }

    @Test
    @DisplayName("a missing key logs a warning once, not once per lookup")
    void missingKeyWarnsOnce() {
        // Possible because logback-classic is already a project dependency. Without it
        // this would only be able to assert the returned value, and a key that logged on
        // every render would go unnoticed.
        Logger logger = (Logger) LoggerFactory.getLogger(Messages.class);
        Level previous = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);

        try {
            String key = "menu.error.definitely.missing";
            assertThat(messages.get(Locale.ENGLISH, key)).isEqualTo(key);
            assertThat(messages.get(Locale.ENGLISH, key)).isEqualTo(key);
            assertThat(messages.get(Locale.ENGLISH, key)).isEqualTo(key);

            List<ILoggingEvent> warnings =
                    appender.list.stream()
                            .filter(event -> event.getLevel() == Level.WARN)
                            .filter(event -> event.getFormattedMessage().contains(key))
                            .toList();
            assertThat(warnings)
                    .as("three lookups of the same missing key must not become three log lines")
                    .hasSize(1);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previous);
        }
    }

    @AfterEach
    void restoreDefaultLocale() {
        // Belt and braces: a test that changes the platform locale must never leave it
        // changed for the rest of the suite.
        Locale.setDefault(Locale.Category.FORMAT, Locale.ENGLISH);
        Locale.setDefault(Locale.Category.DISPLAY, Locale.ENGLISH);
    }
}
