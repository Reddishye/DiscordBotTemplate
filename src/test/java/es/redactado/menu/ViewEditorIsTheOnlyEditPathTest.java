package es.redactado.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fails the build if a rendered container reaches Discord anywhere except through
 * {@code ViewEditor}.
 *
 * <p>Editing the original message is where the components a user sees are decided.
 * Doing it from more than one place means the validation that guards Discord's
 * limits can be skipped, and the rule is easy to state and easy to check, so it is
 * checked mechanically rather than by review.
 */
class ViewEditorIsTheOnlyEditPathTest {

    private static final Path MENU_ROOT = Path.of("src/main/java/es/redactado/menu");
    private static final Path EDITOR = MENU_ROOT.resolve("core/ViewEditor.java");
    private static final String CALL = "editOriginalComponents";

    @Test
    @DisplayName("only ViewEditor edits the original message")
    void onlyViewEditorEdits() throws IOException {
        assertThat(EDITOR).exists();

        try (Stream<Path> files = Files.walk(MENU_ROOT)) {
            List<String> offenders =
                    files.filter(path -> path.toString().endsWith(".java"))
                            .filter(path -> !path.equals(EDITOR))
                            .filter(ViewEditorIsTheOnlyEditPathTest::mentionsEdit)
                            .map(Path::toString)
                            .toList();

            assertThat(offenders).as("%s must only be called from ViewEditor", CALL).isEmpty();
        }
    }

    @Test
    @DisplayName("ViewEditor really does call it, so the rule cannot be satisfied by deletion")
    void editorUsesTheCall() throws IOException {
        assertThat(Files.readString(EDITOR)).contains(CALL);
    }

    private static boolean mentionsEdit(Path file) {
        try {
            return Files.readString(file).contains(CALL);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }
}
