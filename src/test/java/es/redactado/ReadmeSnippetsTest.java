package es.redactado;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every Java block in the README is code that exists.
 *
 * <p>A README drifts from its code quietly: a snippet is edited for clarity, a method is
 * renamed, and the two stop agreeing with nobody noticing. This fails on the disagreement.
 *
 * <p>The comparison ignores whitespace, because a snippet in prose cannot keep the
 * indentation of the method it came from. Everything else has to match exactly, which is
 * what makes the README quotable rather than paraphrased.
 *
 * <p>Only two trees count as quotable: the shipped examples, which exist to be read, and the
 * tests, which contain the fixtures worth showing. A snippet taken from anywhere else would
 * be a copy that starts diverging the day the original changes.
 */
class ReadmeSnippetsTest {

    private static final Path README = Path.of("README.md");

    private static final Pattern JAVA_BLOCK = Pattern.compile("```java\\R(.*?)```", Pattern.DOTALL);

    private static final List<Path> QUOTABLE =
            List.of(Path.of("src/main/java/es/redactado/menu/examples"), Path.of("src/test/java"));

    @Test
    @DisplayName("every Java block in the README appears verbatim in the examples or the tests")
    void everyJavaBlockExistsInTheCode() throws IOException {
        String readme = Files.readString(README);
        Matcher matcher = JAVA_BLOCK.matcher(readme);
        List<String> blocks = new ArrayList<>();
        while (matcher.find()) {
            blocks.add(matcher.group(1));
        }

        assertThat(blocks)
                .as("the README documents the API with real code, so it has blocks")
                .isNotEmpty();

        String corpus = corpus();
        List<String> missing = new ArrayList<>();
        for (String block : blocks) {
            String normalised = normalise(block);
            if (!corpus.contains(normalised)) {
                missing.add("\n---\n" + block.strip() + "\n---");
            }
        }

        assertThat(missing)
                .as(
                        "these Java blocks are not in %s. Take them from an example or a test"
                                + " fixture, or make the code match",
                        QUOTABLE)
                .isEmpty();
    }

    @Test
    @DisplayName("the README links only to files that exist")
    void everyRelativeLinkResolves() throws IOException {
        String readme = Files.readString(README);
        Matcher matcher = Pattern.compile("\\]\\((?!https?://)([^)#]+)").matcher(readme);
        List<String> broken = new ArrayList<>();
        while (matcher.find()) {
            Path target = Path.of(matcher.group(1));
            if (!Files.exists(target)) {
                broken.add(matcher.group(1));
            }
        }

        assertThat(broken).as("relative links in the README that point at nothing").isEmpty();
    }

    /** Every quotable source file, whitespace-normalised. */
    private static String corpus() throws IOException {
        StringBuilder all = new StringBuilder();
        for (Path root : QUOTABLE) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    all.append(normalise(Files.readString(file))).append('\n');
                }
            }
        }
        return all.toString();
    }

    /** Collapses every run of whitespace to one space, so indentation cannot cause a failure. */
    private static String normalise(String source) {
        return source.replaceAll("\\s+", " ").strip();
    }
}
