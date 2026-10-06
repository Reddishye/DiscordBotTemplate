package es.redactado.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Keeps the source files a reader has to hold in their head at a sane size, with the
 * exceptions written down.
 *
 * <p>Three hundred lines is not a law; it is the point at which a class stops being one idea.
 * Past it the answer to "what does this class do" starts needing a list, and a list is what
 * a reader should not need.
 *
 * <p>{@link #OVER_LIMIT} is that list, and each entry says why the split would cost more
 * than it saves. A file that grows past the limit has to be added here with a reason, which
 * is the point: growing quietly is how the limit stops meaning anything.
 *
 * <p>Method length is not gated. A long method is sometimes one linear check, and a line count
 * cannot tell that from a method that has grown four responsibilities.
 */
class ClassSizeTest {

    private static final Path MENU_ROOT = Path.of("src/main/java/es/redactado/menu");

    private static final int MAX_LINES = 300;

    /** Files over the limit, each with the reason it is still one file. */
    private static final Map<String, String> OVER_LIMIT =
            Map.ofEntries(
                    Map.entry(
                            "api/MenuContext.java",
                            "An interface of 25 methods, three quarters of it Javadoc. Splitting"
                                    + " it by role would fragment the one type a menu author"
                                    + " programs against."),
                    Map.entry(
                            "core/BaseContext.java",
                            "One context implementation and the five factories that build it. The"
                                    + " factories are already one-line delegations to a single"
                                    + " private method, so the repetition a split would remove is"
                                    + " already gone."),
                    Map.entry(
                            "core/Incoming.java",
                            "One interaction, seen three ways: button, modal, select. The three"
                                    + " nested records share every field name and every accessor,"
                                    + " which is the cohesion."),
                    Map.entry(
                            "core/MenuRouter.java",
                            "The dispatch pipeline. Owner check, message claim, acknowledgement and"
                                    + " release have to be read together to be reviewed together;"
                                    + " splitting them across files is how that ordering gets"
                                    + " broken without anyone noticing."),
                    Map.entry(
                            "preset/Preset.java",
                            "A nine-component record plus its builder, which is the shape of the"
                                    + " data. The line count is documentation of nine fields and"
                                    + " their invariants."),
                    Map.entry(
                            "preset/PresetDraft.java",
                            "The parsed form and the rules for resolving it, 33 lines over. The"
                                    + " split that made this file also made PresetLoader readable;"
                                    + " a further cut would separate the draft from the only code"
                                    + " that reads it."),
                    Map.entry(
                            "preset/PresetSchema.java",
                            "The JSON shape checks, each a short method returning the first problem"
                                + " it finds. They are one list of rules and read better together"
                                + " than scattered."),
                    Map.entry(
                            "view/ModalForm.java",
                            "A form, its fields and the read that answers it. The read is the"
                                    + " inverse of the build and belongs beside it."),
                    Map.entry(
                            "examples/ShowcaseMenu.java",
                            "An example is read top to bottom. Splitting it would mean reading four"
                                    + " files to see one menu."),
                    Map.entry(
                            "examples/ProfileExampleMenu.java",
                            "The same, and for the same reason: it exists to be read as an example"
                                    + " of a menu with a cache, two forms and four views."));

    @Test
    @DisplayName("no source file grows past the limit without a written reason")
    void noUnexplainedLargeFiles() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MENU_ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                int lines = Files.readAllLines(file).size();
                String relative = MENU_ROOT.relativize(file).toString().replace('\\', '/');
                if (lines > MAX_LINES && !OVER_LIMIT.containsKey(relative)) {
                    offenders.add(relative + " has " + lines + " lines");
                }
            }
        }

        assertThat(offenders)
                .as(
                        "files over %d lines with no entry in OVER_LIMIT. Either split the class"
                                + " or add it with the reason it stays one file",
                        MAX_LINES)
                .isEmpty();
    }

    @Test
    @DisplayName("every entry in the list still exists, so a split removes its reason")
    void theExceptionListIsNotStale() {
        for (String path : OVER_LIMIT.keySet()) {
            assertThat(MENU_ROOT.resolve(path))
                    .as("%s is listed as over the limit but is not there any more", path)
                    .isRegularFile();
        }
    }

    @Test
    @DisplayName("every entry in the list is still over the limit, or nearly so")
    void everyExceptionIsStillNeeded() throws IOException {
        for (String path : OVER_LIMIT.keySet()) {
            int lines = Files.readAllLines(MENU_ROOT.resolve(path)).size();
            assertThat(lines)
                    .as(
                            "%s is listed as over %d lines but is %d. Drop it from OVER_LIMIT; a"
                                    + " stale exception is an excuse",
                            path, MAX_LINES, lines)
                    .isGreaterThan(MAX_LINES - 50);
        }
    }
}
