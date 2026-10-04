package es.redactado.menu;

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
 * The public API is documented, and the packages say what they are for.
 *
 * <p>Two things a reader of this package needs before any method: what the type is for, and
 * what its rules are. Both are Javadoc here, and both are checked, because documentation that
 * is written once and never verified is documentation that has already started to rot.
 *
 * <p>Deliberately not required on: record accessors, trivial getters, and overrides. An
 * override documents itself from the method it overrides, and repeating it is noise.
 */
class ApiJavadocTest {

    private static final Path MENU_ROOT = Path.of("src/main/java/es/redactado/menu");

    /**
     * The public types whose methods are part of the surface a caller writes against.
     *
     * <p>Not every public class: {@code core} and {@code view} types are documented where the
     * rule is interesting, and documenting a builder's twenty accessors individually would be
     * a way of writing less, not more.
     */
    private static final List<String> DOCUMENTED_METHOD_TYPES =
            List.of(
                    "MenuService",
                    "MenuRouter",
                    "Menus",
                    "SimpleMenuBuilder",
                    "ViewBuilder",
                    "RowBuilder",
                    "Nav",
                    "Pager",
                    "Confirm",
                    "SelectMenu",
                    "ModalForm",
                    "Header",
                    "Divider",
                    "Preset");

    /** Packages whose every public type carries Javadoc. */
    private static final List<String> DOCUMENTED_TYPE_PACKAGES = List.of("api", "preset");

    private static final Pattern PUBLIC_TYPE =
            Pattern.compile(
                    "^public (?:final |abstract |sealed |static"
                            + " )*(?:class|interface|enum|record)\\s+(\\w+)");

    private static final Pattern PUBLIC_METHOD =
            Pattern.compile(
                    "^\\s+public (?:static )?(?:final )?[\\w<>\\[\\],.?\\s]+?\\s(\\w+)\\s*\\(");

    /** Accessors and factories whose name says everything the Javadoc would. */
    private static final Pattern TRIVIAL_MEMBER =
            Pattern.compile(
                    "^(get|is|has|to|of|create|builder|equals|hashCode|state|depth|peek|pop|push|"
                            + "count|size|values|warnings|errors|actual|limit)\\w*\\(");

    @Test
    @DisplayName("every public type in api and preset is documented")
    void publicTypesAreDocumented() throws IOException {
        List<String> missing = new ArrayList<>();
        for (String packageName : DOCUMENTED_TYPE_PACKAGES) {
            Path root = MENU_ROOT.resolve(packageName);
            for (Path file : javaFiles(root)) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    java.util.regex.Matcher matcher = PUBLIC_TYPE.matcher(lines.get(i));
                    if (matcher.find() && !documentedAbove(lines, i)) {
                        missing.add(file + ":" + (i + 1) + " " + matcher.group(1));
                    }
                }
            }
        }

        assertThat(missing)
                .as("public types without Javadoc in %s", DOCUMENTED_TYPE_PACKAGES)
                .isEmpty();
    }

    @Test
    @DisplayName("every public method of the documented types is documented")
    void publicMethodsAreDocumented() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path file : javaFiles(MENU_ROOT)) {
            if (!DOCUMENTED_METHOD_TYPES.contains(
                    file.getFileName().toString().replace(".java", ""))) {
                continue;
            }
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                java.util.regex.Matcher matcher = PUBLIC_METHOD.matcher(lines.get(i));
                if (!matcher.find()) {
                    continue;
                }
                String name = matcher.group(1);
                if (TRIVIAL_MEMBER.matcher(name).lookingAt()) {
                    continue;
                }
                if (isOverride(lines, i)) {
                    continue;
                }
                if (!documentedAbove(lines, i)) {
                    missing.add(file + ":" + (i + 1) + " " + file.getFileName() + "." + name);
                }
            }
        }

        assertThat(missing)
                .as("public methods of %s without Javadoc", DOCUMENTED_METHOD_TYPES)
                .isEmpty();
    }

    @Test
    @DisplayName("every package under menu says what it is for")
    void everyPackageHasAnInfoFile() throws IOException {
        List<String> missing = new ArrayList<>();
        try (Stream<Path> directories = Files.walk(MENU_ROOT)) {
            for (Path directory : directories.filter(Files::isDirectory).toList()) {
                if (!Files.exists(directory.resolve("package-info.java"))) {
                    missing.add(directory.toString());
                }
            }
        }

        assertThat(missing)
                .as("a package with no package-info leaves a reader to guess what belongs in it")
                .isEmpty();
    }

    @Test
    @DisplayName("package-info files carry prose, not just a package line")
    void packageInfoFilesSaySomething() throws IOException {
        List<String> thin = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MENU_ROOT)) {
            for (Path file :
                    files.filter(path -> path.getFileName().toString().equals("package-info.java"))
                            .toList()) {
                // The prose is whatever sits between the Javadoc markers, with the markers,
                // the stars and the whitespace taken out: a file that only restates its own
                // name leaves nothing behind.
                String prose = Files.readString(file);
                int open = prose.indexOf("/**");
                int close = prose.indexOf("*/", open);
                prose = open < 0 || close < 0 ? "" : prose.substring(open + 3, close);
                prose = prose.replace("*", "").replace("/", "").replaceAll("\\s+", "");
                if (prose.length() < 40) {
                    thin.add(file.toString());
                }
            }
        }

        assertThat(thin)
                .as(
                        "package-info files with no prose. A reader should not have to guess what a"
                                + " package is for from its name")
                .isEmpty();
    }

    /** Whether the declaration on line {@code index} has a Javadoc block above it. */
    private static boolean documentedAbove(List<String> lines, int index) {
        int i = index - 1;
        while (i >= 0) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("@")) {
                i--;
                continue;
            }
            if (line.endsWith("*/")) {
                int j = i;
                while (j >= 0 && !lines.get(j).contains("/**")) {
                    j--;
                }
                return j >= 0;
            }
            return false;
        }
        return false;
    }

    /** Whether the declaration on line {@code index} carries an {@code @Override}. */
    private static boolean isOverride(List<String> lines, int index) {
        int i = index - 1;
        while (i >= 0) {
            String line = lines.get(i).strip();
            if (line.isEmpty()) {
                i--;
                continue;
            }
            if (line.startsWith("@")) {
                return line.startsWith("@Override");
            }
            return false;
        }
        return false;
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }
}
