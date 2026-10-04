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
 * Fails the build when a colour or an emoji is written outside {@code BuiltinPresets}.
 *
 * <p>These are the two values a preset exists to control. A literal colour in a
 * component is a decision that cannot be restyled, and a literal emoji is a decision
 * that cannot be translated or removed. Both are invisible in review, because a hex
 * value or a small glyph reads as an ordinary detail rather than as a hardcoded
 * decision, and a bot with five of them in five components is no longer themeable.
 *
 * <p>Nothing is exempt. Comments are included, because a hardcoded colour in a Javadoc
 * example is how the rule gets relaxed without anybody deciding to relax it. The only
 * escape is {@code preset/}, and inside it {@code BuiltinPresets} and the two classes
 * that parse emoji.
 */
class NoHardcodedColorsOrEmojiTest {

    private static final Path MAIN_ROOT = Path.of("src/main/java/es/redactado");
    private static final Path ALLOWED = MAIN_ROOT.resolve("menu/preset");

    private static final Pattern COLOR = Pattern.compile("0x[0-9A-Fa-f]{6}");

    private static final Pattern EMOJI_FACTORY =
            Pattern.compile("Emoji\\.from(?:Unicode|Formatted|Custom)\\s*\\(");

    @Test
    @DisplayName("no colour literal and no emoji factory call outside the preset package")
    void nothingHardcodedOutsidePresets() throws IOException {
        assertThat(MAIN_ROOT).isDirectory();

        List<String> colors = new ArrayList<>();
        List<String> emojis = new ArrayList<>();
        int scanned = 0;

        try (Stream<Path> files = Files.walk(MAIN_ROOT)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                if (file.startsWith(ALLOWED)) {
                    continue;
                }
                scanned++;
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).strip();
                    if (COLOR.matcher(line).find()) {
                        colors.add(file + ":" + (i + 1) + ": " + line);
                    }
                    if (EMOJI_FACTORY.matcher(line).find()) {
                        emojis.add(file + ":" + (i + 1) + ": " + line);
                    }
                }
            }
        }

        assertThat(scanned).as("java files scanned").isGreaterThan(0);
        assertThat(colors)
                .as("use Tone and the preset palette, or accentColor for a deliberate exception")
                .isEmpty();
        assertThat(emojis)
                .as("use IconKey and Looks.icon, which resolve through the preset")
                .isEmpty();
    }

    @Test
    @DisplayName("the colour rule catches a real literal")
    void colourRuleIsNotVacuous() {
        assertThat(COLOR.matcher("container.withAccentColor(0x5865F2)").find()).isTrue();
        assertThat(COLOR.matcher("private static final int ACCENT = 0xFFFFFF;").find()).isTrue();
        assertThat(COLOR.matcher("Color.decode(\"#5865F2\")").find())
                .as("a string colour would slip past a hex-only rule")
                .isFalse();
    }

    @Test
    @DisplayName("the emoji rule catches the three factory methods")
    void emojiRuleIsNotVacuous() {
        assertThat(EMOJI_FACTORY.matcher("Emoji.fromUnicode(arrow)").find()).isTrue();
        assertThat(EMOJI_FACTORY.matcher("Emoji.fromFormatted(value)").find()).isTrue();
        assertThat(EMOJI_FACTORY.matcher("Emoji.fromCustom(\"name\", id, false)").find()).isTrue();
        assertThat(EMOJI_FACTORY.matcher("Looks.icon(preset, key)").find()).isFalse();
    }

    @Test
    @DisplayName("the preset package really does hold the colours and emoji")
    void presetPackageIsTheException() throws IOException {
        List<String> presetColors = new ArrayList<>();
        List<String> presetEmoji = new ArrayList<>();

        try (Stream<Path> files = Files.walk(ALLOWED)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (COLOR.matcher(line).find()) {
                        presetColors.add(file.getFileName().toString());
                    }
                    if (EMOJI_FACTORY.matcher(line).find()) {
                        presetEmoji.add(file.getFileName().toString());
                    }
                }
            }
        }

        assertThat(presetColors)
                .as("if the preset package stopped holding colours the scan would be vacuous")
                .isNotEmpty();
        assertThat(presetEmoji)
                .as("EmojiText and Icons are the only places that build emoji")
                .isNotEmpty();
    }
}
