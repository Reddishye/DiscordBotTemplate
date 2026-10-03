package es.redactado.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fails the build when an English literal is written straight into something a user
 * sees.
 *
 * <p><strong>This is a heuristic, not a proof.</strong> It matches a string literal
 * that starts with a letter and sits as the first argument of a call that puts text in
 * front of someone: {@code ephemeral(}, {@code sendMessage(}, {@code reply(},
 * {@code setContent(}, {@code TextDisplay.of(}, {@code Section.of(}, and the button
 * factories. The point is that the common mistake, pasting English directly into a
 * reply, fails the build instead of surviving to a translator.
 *
 * <p>Its limits are known and listed in NOTES.md:
 *
 * <ul>
 *   <li>A literal that starts with markdown or an emoji, such as {@code "*Not set*"},
 *       is not matched, because the leading character is not a letter.
 *   <li>A literal passed through a constant or a local variable is not matched, only a
 *       literal in argument position. In particular {@code Replies.ephemeral} takes the
 *       event first, so a literal second argument is missed, and that is the reply path
 *       every user-facing message in this package travels. Asserted by
 *       {@link #knownGapIsReal()} so the gap cannot be quietly forgotten.
 *   <li>A literal built by concatenation, or by {@code formatted}, is not matched.
 * </ul>
 *
 * <p>All three were real during this change and were localized anyway; the scan is a
 * net for new mistakes, not a substitute for reading the diff. Text meant for
 * developers, such as the message of an {@code IllegalArgumentException} or any log
 * line, is not user-facing and is deliberately out of scope.
 */
class NoHardcodedUserTextTest {

    private static final Path MENU_ROOT = Path.of("src/main/java/es/redactado/menu");

    private static final Pattern HARDCODED =
            Pattern.compile(
                    "\\b(?:ephemeral|sendMessage|reply|setContent|TextDisplay\\.of|Section\\.of"
                        + "|Button\\.(?:primary|secondary|success|danger|of))\\s*\\(\\s*\"([A-Za-z][^\"]*)\"");

    @Test
    @DisplayName("no English literal is passed straight to a user-facing call")
    void noHardcodedUserText() throws IOException {
        assertThat(MENU_ROOT).isDirectory();

        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        try (Stream<Path> files = Files.walk(MENU_ROOT)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                scanned++;
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    var matcher = HARDCODED.matcher(line);
                    while (matcher.find()) {
                        offenders.add(file + ": " + line.strip());
                    }
                }
            }
        }

        assertThat(scanned).as("java files scanned").isGreaterThan(0);
        assertThat(offenders)
                .as("pass a MessageKeys constant or MenuContext.t instead of a literal")
                .isEmpty();
    }

    @Test
    @DisplayName("the detector catches a literal that really is user-facing")
    void detectorFlagsRealOffences() {
        List<String> flagged =
                linesFlagged(
                        // Every one of these would show English to a user.
                        "event.reply(\"Unknown action.\");",
                        "event.getHook().sendMessage(\"The bot is busy.\")",
                        "action.setContent(\"Invalid parameter\")",
                        "TextDisplay.of(\"No items.\")",
                        "Section.of(\"Header text\")",
                        "Button.primary(\"Click me\", id, handler)");

        assertThat(flagged).hasSize(6);
    }

    @Test
    @DisplayName("a literal behind the event argument is a known gap, asserted not assumed")
    void knownGapIsReal() {
        // Replies.ephemeral takes the event first, so a literal second argument is not in
        // the position this scan inspects. Every user-facing reply in the menu package
        // travels through that method, so this gap covers the most important call site
        // there is. It is recorded rather than closed: widening the rule to any argument
        // position would also flag format strings and developer-facing text, and the scan
        // would become noise nobody keeps enabled.
        assertThat(linesFlagged("Replies.ephemeral(event, \"This menu is not yours.\");"))
                .isEmpty();
    }

    @Test
    @DisplayName("the detector leaves legitimate code alone")
    void detectorIgnoresLegitimateCode() {
        List<String> flagged =
                linesFlagged(
                        // Resolved through the bundles, which is the point.
                        "Replies.ephemeral(event, messages, locale, MessageKeys.ERROR_BUSY);",
                        "ctx.t(MessageKeys.LIST_EMPTY)",
                        // Markdown and emoji lead with a non-letter.
                        "TextDisplay.of(\"*Not set*\")",
                        "TextDisplay.of(ctx.t(MessageKeys.LIST_EMPTY))",
                        // Developer-facing text and format templates are not user-facing.
                        "throw new IllegalArgumentException(\"level must be between 1 and 3\")",
                        "TextDisplay.of(\"**%s:** %s\".formatted(label, value))",
                        // Other calls entirely.
                        "LOG.warn(\"Container has {} children\", count)",
                        "preset.palette().accent()");

        assertThat(flagged).isEmpty();
    }

    private static List<String> linesFlagged(String... lines) {
        List<String> flagged = new ArrayList<>();
        for (String line : lines) {
            if (HARDCODED.matcher(line).find()) {
                flagged.add(line);
            }
        }
        return flagged;
    }
}
