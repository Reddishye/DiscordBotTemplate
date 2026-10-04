package es.redactado.menu.preset;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fails the build if the {@code preset} package imports another menu package.
 *
 * <p>{@code preset} describes how a menu looks and deliberately knows nothing about
 * menus: no router, no session, no container. That is what lets a preset be read and
 * tested with nothing else on the classpath, and what keeps the look of a bot
 * separable from its behaviour. The rule is cheap to state and easy to break by
 * accident, since reaching for a type from a sibling package is the obvious thing to
 * do when a compiler offers it.
 *
 * <p>The same scan already exists for {@code AsciiSourcesTest} and
 * {@code ViewEditorIsTheOnlyEditPathTest}; this follows their shape.
 */
class PresetDependsOnNothingTest {

    private static final Path PACKAGE_ROOT = Path.of("src/main/java/es/redactado/menu/preset");

    private static final Pattern FORBIDDEN =
            Pattern.compile("^import\\s+(static\\s+)?es\\.redactado\\.menu\\.(?!preset\\b)\\w+");

    @Test
    @DisplayName("the preset package imports no other menu package")
    void presetImportsNothingFromMenu() throws IOException {
        assertThat(PACKAGE_ROOT).isDirectory();

        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        try (Stream<Path> files = Files.walk(PACKAGE_ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                scanned++;
                Files.readAllLines(file).stream()
                        .map(String::trim)
                        .filter(line -> FORBIDDEN.matcher(line).find())
                        .forEach(line -> offenders.add(file + ": " + line));
            }
        }

        assertThat(scanned).as("java files scanned").isGreaterThan(0);
        assertThat(offenders).as("preset must not depend on api, core or view").isEmpty();
    }

    @Test
    @DisplayName("the rule is not vacuous: a forbidden import is detected")
    void detectorIsNotVacuous() {
        assertThat(FORBIDDEN.matcher("import es.redactado.menu.core.MenuRouter;").find()).isTrue();
        assertThat(
                        FORBIDDEN
                                .matcher("import static es.redactado.menu.view.Limits.MAX_ROWS;")
                                .find())
                .isTrue();
    }

    @Test
    @DisplayName("sibling imports inside the package, and JDA, are allowed")
    void allowedImportsAreNotFlagged() {
        assertThat(FORBIDDEN.matcher("import es.redactado.menu.preset.Preset;").find()).isFalse();
        assertThat(
                        FORBIDDEN
                                .matcher("import es.redactado.menu.preset.BuiltinPresets;all();")
                                .find())
                .isFalse();
        assertThat(FORBIDDEN.matcher("import net.dv8tion.jda.api.entities.emoji.Emoji;").find())
                .isFalse();
        assertThat(FORBIDDEN.matcher("import java.util.Map;").find()).isFalse();
    }
}
