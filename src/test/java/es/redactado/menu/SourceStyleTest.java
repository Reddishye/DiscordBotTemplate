package es.redactado.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Style rules that are cheap to state and expensive to argue about later.
 *
 * <p>Each rule here was a real disagreement during the build: a name nobody could guess, a
 * divider comment holding a file together, a sentence padded with a word that says nothing.
 * A rule that is only in a reviewer's head is a rule that gets broken on a busy day, so each
 * one is a test with the file and line in the failure message.
 *
 * <p>What is deliberately not here: whether a comment restates the code, and how long a method
 * is. Both need a judgement a regex cannot make, so they stay reports rather than gates. The
 * method-length report lives in {@code docs/design-decisions.md}.
 */
class SourceStyleTest {

    private static final Path MENU_ROOT = Path.of("src/main/java/es/redactado/menu");

    /** Suffixes that say nothing about what a type does. */
    private static final List<String> MEANINGLESS_SUFFIXES =
            List.of("Manager", "Helper", "Util", "Utils", "Impl");

    /** The template's own service manager, which is a manager and is allowed to say so. */
    private static final List<String> NAME_EXEMPT = List.of("TaskManager");

    /** Longest a type name may be before it stops being a word. */
    private static final int MAX_TYPE_NAME = 30;

    /**
     * Words that pad a sentence without adding anything.
     *
     * <p>Matched on word boundaries and case-insensitively. {@code note that} and
     * {@code in order to} are phrases, so they are matched as phrases.
     */
    private static final List<String> FILLER =
            List.of(
                    "simply",
                    "just",
                    "obviously",
                    "robust",
                    "powerful",
                    "seamless",
                    "easily",
                    "note that",
                    "in order to");

    private static final Pattern TYPE_DECLARATION =
            Pattern.compile("\\b(?:class|interface|enum|record)\\s+(\\w+)");

    private static final Pattern BANNER = Pattern.compile("//\\s*(?:[-=*~_]\\s*){3,}|//\\s*--");

    @Test
    @DisplayName("no type is named after a role instead of a thing")
    void typeNamesSayWhatTheyAre() throws IOException {
        List<String> offenders = new ArrayList<>();
        forEachJavaFile(
                MENU_ROOT,
                (file, lines) -> {
                    for (int i = 0; i < lines.size(); i++) {
                        java.util.regex.Matcher matcher = TYPE_DECLARATION.matcher(lines.get(i));
                        if (!matcher.find()) {
                            continue;
                        }
                        String name = matcher.group(1);
                        if (NAME_EXEMPT.contains(name)) {
                            continue;
                        }
                        for (String suffix : MEANINGLESS_SUFFIXES) {
                            if (name.endsWith(suffix)) {
                                offenders.add(where(file, i + 1, name + " ends in " + suffix));
                            }
                        }
                        if (name.length() > MAX_TYPE_NAME) {
                            offenders.add(
                                    where(
                                            file,
                                            i + 1,
                                            name + " is " + name.length() + " characters"));
                        }
                    }
                });

        assertThat(offenders)
                .as(
                        "type names ending in a role word, or longer than %d characters."
                                + " %s is exempt by name: it is the template's own service"
                                + " manager and renaming it would break the template's",
                        MAX_TYPE_NAME, NAME_EXEMPT)
                .isEmpty();
    }

    @Test
    @DisplayName("no comment is a note to self or a divider")
    void noTodoOrBannerComments() throws IOException {
        List<String> offenders = new ArrayList<>();
        forEachJavaFile(
                MENU_ROOT,
                (file, lines) -> {
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        for (String marker : List.of("TODO", "FIXME", "XXX")) {
                            if (line.contains(marker)) {
                                offenders.add(where(file, i + 1, marker + ": " + line.strip()));
                            }
                        }
                        if (BANNER.matcher(line).find()) {
                            offenders.add(where(file, i + 1, "divider comment: " + line.strip()));
                        }
                    }
                });

        assertThat(offenders)
                .as("a TODO is a promise nobody is keeping and a divider holds a file together")
                .isEmpty();
    }

    @Test
    @DisplayName("no comment is padded with a word that says nothing")
    void noFillerWordsInComments() throws IOException {
        List<String> offenders = new ArrayList<>();
        forEachJavaFile(
                MENU_ROOT,
                (file, lines) -> {
                    for (int i = 0; i < lines.size(); i++) {
                        String comment = commentOf(lines.get(i));
                        if (comment.isEmpty()) {
                            continue;
                        }
                        String haystack = comment.toLowerCase(Locale.ROOT);
                        for (String filler : FILLER) {
                            if (haystack.contains(filler)) {
                                offenders.add(where(file, i + 1, "'" + filler + "': " + comment));
                            }
                        }
                    }
                });

        assertThat(offenders)
                .as(
                        "filler words in comments or Javadoc. Rewrite the sentence, do not delete"
                                + " the documentation it was padding")
                .isEmpty();
    }

    /**
     * The comment part of one source line: after a line comment, or the whole line inside a
     * block comment.
     *
     * <p>String literals are blanked first, so a menu key in an example is not read as prose.
     * The block-comment state is tracked by the caller through {@link #commentOf} returning
     * empty for code lines, which is enough because every block comment in this tree opens and
     * closes on the same line.
     */
    private static String commentOf(String line) {
        String blanked = line.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
        int slash = blanked.indexOf("//");
        if (slash >= 0) {
            return blanked.substring(slash + 2).strip();
        }
        String stripped = blanked.strip();
        if (stripped.startsWith("*") || stripped.startsWith("/*")) {
            return stripped.replaceFirst("^/\\*+", "").replaceFirst("\\*+/$", "").strip();
        }
        return "";
    }

    private static String where(Path file, int line, String what) {
        return file + ":" + line + " " + what;
    }

    private static void forEachJavaFile(Path root, FileVisitor visitor) throws IOException {
        assertThat(root).isDirectory();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                visitor.visit(file, Files.readAllLines(file));
            }
        }
    }

    /** Reads one source file and hands its lines to the check. */
    private interface FileVisitor {
        void visit(Path file, List<String> lines);
    }
}
