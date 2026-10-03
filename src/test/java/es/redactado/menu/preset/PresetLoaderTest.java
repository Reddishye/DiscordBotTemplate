package es.redactado.menu.preset;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the JSON preset format, one situation per test.
 *
 * <p>The fixture is {@code docs/presets/ocean.json}, the same file the documentation
 * shows, so the two cannot disagree.
 */
class PresetLoaderTest {

    private static final Collection<Preset> BUILTINS = BuiltinPresets.all();

    @TempDir Path directory;

    @Nested
    @DisplayName("loading")
    class Loading {

        @Test
        @DisplayName("the documented fixture loads and inherits from its parent")
        void fixtureLoads() {
            LoadResult result = PresetLoader.load(Path.of("docs/presets"), BUILTINS);

            assertThat(result.problems()).isEmpty();
            assertThat(result.presets()).extracting(Preset::name).containsExactly("ocean");
            Preset ocean = result.presets().getFirst();

            assertThat(ocean.description()).isEqualTo("Cool blue theme for support menus.");

            assertThat(ocean.palette().accent()).isEqualTo(0x1E90FF);
            assertThat(ocean.palette().success()).isEqualTo(0x2ECC71);
            assertThat(ocean.palette().warning()).isEqualTo(0xF5A623);
            assertThat(ocean.palette().danger()).isEqualTo(0xFF5A5F);
            assertThat(ocean.palette().info()).isEqualTo(0x4EA8DE);
            assertThat(ocean.palette().neutral()).isEqualTo(0x3A3F4B);

            assertThat(ocean.icons().formatted(IconKey.OK)).isEqualTo("\uD83D\uDE80");
            assertThat(ocean.icons().formatted(IconKey.BACK)).isEqualTo("\u2B07\uFE0F");
            assertThat(ocean.icons().formatted(IconKey.DELETE)).isEmpty();
            assertThat(ocean.icons().formatted(IconKey.WARN))
                    .isEqualTo(BuiltinPresets.MIDNIGHT.icons().formatted(IconKey.WARN));

            assertThat(ocean.density()).isEqualTo(Density.COMFORTABLE);
            assertThat(ocean.divider().visible()).isTrue();
            assertThat(ocean.divider().gap()).isEqualTo(Gap.LARGE);
            assertThat(ocean.header().level()).isEqualTo(2);
            assertThat(ocean.header().subtitle()).isTrue();
            assertThat(ocean.buttons().of(ButtonRole.PRIMARY))
                    .isEqualTo(net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY);
            assertThat(ocean.buttons().of(ButtonRole.DANGER))
                    .isEqualTo(net.dv8tion.jda.api.components.buttons.ButtonStyle.DANGER);
            assertThat(ocean.footer()).isEqualTo("{menu}");
        }

        @Test
        @DisplayName("a file with only a name is the default look")
        void nameOnlyUsesDefaults() throws IOException {
            write("plain", "{\"name\": \"plain\"}");

            LoadResult result = load();

            assertThat(result.problems()).isEmpty();
            assertThat(result.presets().getFirst())
                    .as("no extends means the builder defaults, which are the default preset")
                    .isEqualTo(Preset.builder("plain").build());
        }

        @Test
        @DisplayName("a partial palette overrides only the colours it lists")
        void partialPaletteMerges() throws IOException {
            write("tint", "{\"name\": \"tint\", \"palette\": {\"danger\": \"#FF0000\"}}");

            Preset preset = load().presets().getFirst();

            assertThat(preset.palette().danger()).isEqualTo(0xFF0000);
            assertThat(preset.palette().accent())
                    .isEqualTo(BuiltinPresets.DEFAULT.palette().accent());
            assertThat(preset.palette().neutral())
                    .isEqualTo(BuiltinPresets.DEFAULT.palette().neutral());
        }

        @Test
        @DisplayName("an empty icon value removes an inherited icon")
        void emptyIconRemoves() throws IOException {
            write("bare", "{\"name\": \"bare\", \"icons\": {\"ok\": \"\"}}");

            Preset preset = load().presets().getFirst();

            assertThat(preset.icons().formatted(IconKey.OK)).isEmpty();
            assertThat(preset.icons().formatted(IconKey.WARN)).isNotEmpty();
        }

        @Test
        @DisplayName("a partial divider, header and footer override field by field")
        void partialObjectsMerge() throws IOException {
            write(
                    "bits",
                    "{\"name\": \"bits\", \"header\": {\"subtitle\": true},"
                            + " \"divider\": {\"gap\": \"large\"}, \"footer\": \"{user}\"}");

            Preset preset = load().presets().getFirst();

            assertThat(preset.header().level()).isEqualTo(3);
            assertThat(preset.header().subtitle()).isTrue();
            assertThat(preset.divider().visible()).isTrue();
            assertThat(preset.divider().gap()).isEqualTo(Gap.LARGE);
            assertThat(preset.footer()).isEqualTo("{user}");
        }

        @Test
        @DisplayName("a missing directory is not a problem")
        void missingDirectoryIsEmpty() {
            LoadResult result = PresetLoader.load(directory.resolve("absent"), BUILTINS);

            assertThat(result.presets()).isEmpty();
            assertThat(result.problems()).isEmpty();
            assertThat(result.clean()).isTrue();
        }

        @Test
        @DisplayName("files are read in sorted order and results are sorted by name")
        void deterministicOrder() throws IOException {
            write("zulu", "{\"name\": \"zulu\"}");
            write("alpha", "{\"name\": \"alpha\"}");
            write("mike", "{\"name\": \"mike\"}");

            LoadResult first = load();
            LoadResult second = load();

            assertThat(first.presets())
                    .extracting(Preset::name)
                    .containsExactly("alpha", "mike", "zulu");
            assertThat(second.presets())
                    .extracting(Preset::name)
                    .containsExactly("alpha", "mike", "zulu");
        }

        @Test
        @DisplayName("a chain of two custom presets resolves")
        void chainedInheritance() throws IOException {
            write("base", "{\"name\": \"base\", \"density\": \"compact\"}");
            write("child", "{\"name\": \"child\", \"extends\": \"base\", \"footer\": \"hi\"}");

            LoadResult result = load();

            assertThat(result.problems()).isEmpty();
            assertThat(result.presets()).extracting(Preset::name).containsExactly("base", "child");
            assertThat(result.presets().getFirst().density()).isEqualTo(Density.COMPACT);
            assertThat(result.presets().get(1).density()).isEqualTo(Density.COMPACT);
            assertThat(result.presets().get(1).footer()).isEqualTo("hi");
        }
    }

    @Nested
    @DisplayName("problems")
    class Problems {

        @Test
        @DisplayName("invalid JSON is rejected")
        void invalidJson() throws IOException {
            write("broken", "{ not json");

            assertOnly("broken", "is not valid JSON");
        }

        @Test
        @DisplayName("a duplicate key is rejected")
        void duplicateKey() throws IOException {
            write("twice", "{\"name\": \"twice\", \"name\": \"again\"}");

            assertOnly("twice", "Duplicate field");
        }

        @Test
        @DisplayName("an unknown property at the root is rejected")
        void unknownRootProperty() throws IOException {
            write("odd", "{\"name\": \"odd\", \"colour\": \"#FFFFFF\"}");

            assertOnly("odd", "colour: unknown property");
        }

        @Test
        @DisplayName("an unknown property inside an object is rejected")
        void unknownNestedProperty() throws IOException {
            write("odd", "{\"name\": \"odd\", \"palette\": {\"background\": \"#000000\"}}");

            assertOnly("odd", "palette.background: unknown property");
        }

        @Test
        @DisplayName("a colour that is not #RRGGBB is rejected, naming the field")
        void badColor() throws IOException {
            write("blue", "{\"name\": \"blue\", \"palette\": {\"accent\": \"blue\"}}");

            assertOnly("blue", "palette.accent: expected #RRGGBB, got 'blue'");
        }

        @Test
        @DisplayName("an unknown enum word is rejected, listing the choices")
        void badEnumWord() throws IOException {
            write("roomy", "{\"name\": \"roomy\", \"density\": \"roomy\"}");

            assertOnly("roomy", "density: expected compact|normal|comfortable, got 'roomy'");
        }

        @Test
        @DisplayName("a header level outside 1..3 is rejected")
        void badLevel() throws IOException {
            write("tall", "{\"name\": \"tall\", \"header\": {\"level\": 5}}");

            assertOnly("tall", "header.level: expected 1..3, got 5");
        }

        @Test
        @DisplayName("an unsupported footer placeholder is rejected, naming it")
        void badPlaceholder() throws IOException {
            write("greeter", "{\"name\": \"greeter\", \"footer\": \"hi {who}\"}");

            assertOnly(
                    "greeter",
                    "footer: placeholder '{who}' is not supported, only {user} and {menu} are");
        }

        @Test
        @DisplayName("a name that differs from the file name is rejected")
        void nameMustMatchFile() throws IOException {
            write("ocean", "{\"name\": \"sea\"}");

            assertOnly("ocean", "name: is 'sea' but the file is named 'ocean.json'");
        }

        @Test
        @DisplayName("a name that is a built-in is rejected")
        void nameCannotShadowBuiltin() throws IOException {
            write("midnight", "{\"name\": \"midnight\", \"density\": \"compact\"}");

            assertOnly("midnight", "name: 'midnight' is a built-in preset");
        }

        @Test
        @DisplayName("a file over 64 KiB is rejected")
        void oversizedFile() throws IOException {
            write("big", "{\"name\": \"big\", \"description\": \"" + "x".repeat(70 * 1024) + "\"}");

            assertOnly("big", "file is too large: ");
        }

        @Test
        @DisplayName("a missing parent is reported")
        void missingParent() throws IOException {
            write("orphan", "{\"name\": \"orphan\", \"extends\": \"ghost\"}");

            assertOnly("orphan", "extends: no preset named 'ghost'");
        }

        @Test
        @DisplayName("every file in a two-file cycle is reported")
        void twoFileCycle() throws IOException {
            write("one", "{\"name\": \"one\", \"extends\": \"two\"}");
            write("two", "{\"name\": \"two\", \"extends\": \"one\"}");

            LoadResult result = load();

            assertThat(result.presets()).isEmpty();
            assertThat(result.problems()).hasSize(2);
            assertThat(messagesFor(result, "one"))
                    .containsExactly("extends: is part of a cycle with two.json");
            assertThat(messagesFor(result, "two"))
                    .containsExactly("extends: is part of a cycle with one.json");
        }

        @Test
        @DisplayName("a self-cycle is reported")
        void selfCycle() throws IOException {
            write("loop", "{\"name\": \"loop\", \"extends\": \"loop\"}");

            assertOnly("loop", "extends: is a cycle with itself");
        }

        @Test
        @DisplayName("a child of a file that failed to parse is reported")
        void childOfFailedParent() throws IOException {
            write("parent", "{ broken");
            write("child", "{\"name\": \"child\", \"extends\": \"parent\"}");

            LoadResult result = load();

            assertThat(result.presets()).isEmpty();
            assertThat(messagesFor(result, "parent")).hasSize(1);
            assertThat(messagesFor(result, "child"))
                    .containsExactly("extends: parent 'parent' failed to load");
        }

        @Test
        @DisplayName("201 files are capped at 200, reported as one problem")
        void tooManyFiles() throws IOException {
            for (int i = 0; i < 201; i++) {
                write("p%03d".formatted(i), "{\"name\": \"p%03d\"}".formatted(i));
            }

            LoadResult result = load();

            assertThat(result.presets()).hasSize(200);
            assertThat(result.problems()).hasSize(1);
            assertThat(result.problems().getFirst().message())
                    .isEqualTo("too many files: found 201, reading the first 200");
        }

        @Test
        @DisplayName("one bad file does not stop the others loading")
        void badFileIsIsolated() throws IOException {
            write("good", "{\"name\": \"good\"}");
            write("bad", "{\"name\": \"bad\", \"density\": \"roomy\"}");

            LoadResult result = load();

            assertThat(result.presets()).extracting(Preset::name).containsExactly("good");
            assertThat(result.problems()).hasSize(1);
        }

        @Test
        @DisplayName("an unknown icon key is rejected")
        void unknownIconKey() throws IOException {
            write("odd", "{\"name\": \"odd\", \"icons\": {\"sparkle\": \"\\u2728\"}}");

            assertOnly("odd", "icons.sparkle: unknown icon key");
        }

        @Test
        @DisplayName("an icon that is not an emoji is rejected")
        void badIcon() throws IOException {
            write("odd", "{\"name\": \"odd\", \"icons\": {\"ok\": \"banana\"}}");

            assertOnly("odd", "icons.ok: expected a Unicode emoji");
        }

        @Test
        @DisplayName("a non-.json file is ignored")
        void nonJsonIgnored() throws IOException {
            write("good", "{\"name\": \"good\"}");
            Files.writeString(directory.resolve("notes.txt"), "hello");

            LoadResult result = load();

            assertThat(result.problems()).isEmpty();
            assertThat(result.presets()).extracting(Preset::name).containsExactly("good");
        }
    }

    private LoadResult load() {
        return PresetLoader.load(directory, BUILTINS);
    }

    private void write(String name, String json) throws IOException {
        Files.writeString(directory.resolve(name + ".json"), json, StandardCharsets.UTF_8);
    }

    /** Asserts the one problem, and that it names this file and contains the text. */
    private void assertOnly(String file, String contains) {
        LoadResult result = load();
        assertThat(result.presets()).isEmpty();
        assertThat(result.problems()).hasSize(1);
        LoadProblem problem = result.problems().getFirst();
        assertThat(problem.file()).isEqualTo(file + ".json");
        assertThat(problem.message()).contains(contains);
        assertThat(problem)
                .as("the rendered message names the file then the field path")
                .hasToString(file + ".json: " + problem.message());
    }

    private static List<String> messagesFor(LoadResult result, String file) {
        return result.problems().stream()
                .filter(problem -> problem.file().equals(file + ".json"))
                .map(LoadProblem::message)
                .toList();
    }
}
