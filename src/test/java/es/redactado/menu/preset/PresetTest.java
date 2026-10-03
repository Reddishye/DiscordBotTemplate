package es.redactado.menu.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PresetTest {

    private static final String CHECK = Character.toString(0x2705);

    @Nested
    @DisplayName("name")
    class Name {

        @Test
        @DisplayName("accepts the pattern")
        void acceptsPattern() {
            assertThat(BuiltinPresets.all())
                    .allSatisfy(
                            preset ->
                                    assertThat(preset.name()).matches("[a-z0-9][a-z0-9_-]{0,31}"));
        }

        @Test
        @DisplayName("accepts a name of exactly 32 characters")
        void acceptsThirtyTwo() {
            String name = "a".repeat(32);

            assertThat(Preset.builder(name).name(name).build().name()).isEqualTo(name);
        }

        @Test
        @DisplayName("rejects a name of 33 characters, naming it")
        void rejectsThirtyThree() {
            String name = "a".repeat(33);

            assertThatThrownBy(() -> Preset.builder(name).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(name);
        }

        @Test
        @DisplayName("rejects an uppercase, spaced, or empty name")
        void rejectsMalformed() {
            for (String bad : new String[] {"Default", "with space", "", "-leading", "a/b"}) {
                assertThatThrownBy(() -> Preset.builder(bad).build())
                        .as(bad)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("preset name");
            }
        }
    }

    @Nested
    @DisplayName("description")
    class Description {

        @Test
        @DisplayName("accepts exactly 120 characters")
        void acceptsBoundary() {
            String description = "d".repeat(120);

            assertThat(Preset.builder("ok").description(description).build().description())
                    .isEqualTo(description);
        }

        @Test
        @DisplayName("rejects 121 characters, naming the preset and the field")
        void rejectsTooLong() {
            assertThatThrownBy(() -> Preset.builder("mine").description("d".repeat(121)).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mine")
                    .hasMessageContaining("description");
        }
    }

    @Nested
    @DisplayName("footer")
    class Footer {

        @Test
        @DisplayName("accepts an empty footer")
        void acceptsEmpty() {
            assertThat(Preset.builder("mine").footer("").build().footer()).isEmpty();
        }

        @Test
        @DisplayName("accepts exactly 200 characters")
        void acceptsBoundary() {
            String footer = "f".repeat(200);

            assertThat(Preset.builder("mine").footer(footer).build().footer()).isEqualTo(footer);
        }

        @Test
        @DisplayName("rejects 201 characters, naming the preset and the field")
        void rejectsTooLong() {
            assertThatThrownBy(() -> Preset.builder("mine").footer("f".repeat(201)).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mine")
                    .hasMessageContaining("footer");
        }

        @Test
        @DisplayName("accepts the supported placeholders")
        void acceptsPlaceholders() {
            assertThat(Preset.builder("mine").footer("{user} in {menu}").build().footer())
                    .isEqualTo("{user} in {menu}");
        }

        @Test
        @DisplayName("rejects any other placeholder, naming it")
        void rejectsUnknownPlaceholder() {
            assertThatThrownBy(() -> Preset.builder("mine").footer("hello {name}").build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mine")
                    .hasMessageContaining("{name}");
        }

        @Test
        @DisplayName("accepts an empty footer")
        void acceptsEmptyFooter() {
            assertThat(Preset.builder("none").footer("").build().footer()).isEmpty();
        }

        @Test
        @DisplayName("accepts a footer that is only placeholders")
        void acceptsPlaceholderOnlyFooter() {
            assertThat(Preset.builder("mine").footer("{user} opened {menu}").build().footer())
                    .isEqualTo("{user} opened {menu}");
        }
    }

    @Nested
    @DisplayName("palette")
    class PaletteRule {

        @Test
        @DisplayName("accepts the boundary colours")
        void acceptsBoundaries() {
            assertThat(new Palette(0, Palette.MAX, 0, Palette.MAX, 0, Palette.MAX)).isNotNull();
        }

        @Test
        @DisplayName("rejects a colour outside 0..0xFFFFFF, naming the field")
        void rejectsOutOfRange() {
            assertThatThrownBy(() -> new Palette(0x1000000, 0, 0, 0, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("accent");
            assertThatThrownBy(() -> new Palette(0, -1, 0, 0, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("success");
        }
    }

    @Nested
    @DisplayName("header")
    class HeaderRule {

        @Test
        @DisplayName("accepts levels 1 to 3")
        void acceptsLevels() {
            for (int level = 1; level <= 3; level++) {
                assertThat(new HeaderStyle(level, false).level()).isEqualTo(level);
            }
        }

        @Test
        @DisplayName("rejects a level outside 1 to 3")
        void rejectsLevels() {
            assertThatThrownBy(() -> new HeaderStyle(0, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("header level");
            assertThatThrownBy(() -> new HeaderStyle(4, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("header level");
        }
    }

    @Nested
    @DisplayName("builder")
    class BuilderRule {

        @Test
        @DisplayName("defaults match the default preset")
        void defaultsMatchDefaultPreset() {
            Preset built = Preset.builder("mine").build();

            assertThat(built.palette()).isEqualTo(BuiltinPresets.DEFAULT.palette());
            assertThat(built.icons()).isEqualTo(BuiltinPresets.DEFAULT.icons());
            assertThat(built.density()).isEqualTo(BuiltinPresets.DEFAULT.density());
            assertThat(built.divider()).isEqualTo(BuiltinPresets.DEFAULT.divider());
            assertThat(built.header()).isEqualTo(BuiltinPresets.DEFAULT.header());
            assertThat(built.buttons()).isEqualTo(BuiltinPresets.DEFAULT.buttons());
            assertThat(built.footer()).isEqualTo(BuiltinPresets.DEFAULT.footer());
        }

        @Test
        @DisplayName("toBuilder changes only the named field")
        void toBuilderChangesOneField() {
            Preset original = BuiltinPresets.MINIMAL;

            Preset changed = original.toBuilder().density(Density.COMFORTABLE).build();

            assertThat(changed.density()).isEqualTo(Density.COMFORTABLE);
            assertThat(changed.name()).isEqualTo(original.name());
            assertThat(changed.description()).isEqualTo(original.description());
            assertThat(changed.palette()).isEqualTo(original.palette());
            assertThat(changed.icons()).isEqualTo(original.icons());
            assertThat(changed.divider()).isEqualTo(original.divider());
            assertThat(changed.header()).isEqualTo(original.header());
            assertThat(changed.buttons()).isEqualTo(original.buttons());
            assertThat(changed.footer()).isEqualTo(original.footer());
        }

        @Test
        @DisplayName("rejects a null field, naming the preset")
        void rejectsNullField() {
            assertThatThrownBy(() -> Preset.builder("mine").palette(null).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("mine");
        }
    }

    @Nested
    @DisplayName("icons")
    class IconRule {

        @Test
        @DisplayName("rejects a value that is not an emoji, naming the key")
        void rejectsInvalidValue() {
            Map<IconKey, String> values = new HashMap<>();
            values.put(IconKey.OK, "definitely not an emoji");

            assertThatThrownBy(() -> Icons.of(values))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("OK")
                    .hasMessageContaining("not a valid emoji");
        }

        @Test
        @DisplayName("treats a blank value as no icon")
        void blankMeansAbsent() {
            Map<IconKey, String> values = new HashMap<>();
            values.put(IconKey.OK, "   ");

            Icons icons = Icons.of(values);

            assertThat(icons.get(IconKey.OK)).isEmpty();
            assertThat(icons.formatted(IconKey.OK)).isEmpty();
        }

        @Test
        @DisplayName("formatted returns empty for an absent key")
        void formattedEmptyForAbsent() {
            assertThat(Icons.none().formatted(IconKey.BACK)).isEmpty();
        }

        @Test
        @DisplayName("with returns a new instance and leaves the original alone")
        void withIsImmutable() {
            Icons original = Icons.none();

            Icons changed = original.with(IconKey.OK, CHECK);

            assertThat(changed).isNotSameAs(original);
            assertThat(original.get(IconKey.OK)).isEmpty();
            assertThat(changed.get(IconKey.OK)).isPresent();
        }

        @Test
        @DisplayName("with a blank value removes the icon")
        void withBlankRemoves() {
            Icons icons = Icons.none().with(IconKey.OK, CHECK);

            assertThat(icons.with(IconKey.OK, " ").get(IconKey.OK)).isEmpty();
        }

        @Test
        @DisplayName("equals and hashCode follow the configured values")
        void equalsAndHashCode() {
            Icons a = Icons.none().with(IconKey.OK, CHECK);
            Icons b = Icons.none().with(IconKey.OK, CHECK);
            Icons c = Icons.none().with(IconKey.WARN, CHECK);

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
            assertThat(a).isNotEqualTo(c);
            assertThat(a.toString()).contains("OK");
        }

        @Test
        @DisplayName("asMap is unmodifiable")
        void asMapUnmodifiable() {
            Icons icons = Icons.none().with(IconKey.OK, CHECK);

            assertThat(icons.asMap()).containsEntry(IconKey.OK, CHECK);
            assertThatThrownBy(() -> icons.asMap().put(IconKey.WARN, CHECK))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("accepts a custom emoji mention")
        void acceptsCustomMention() {
            Icons icons = Icons.none().with(IconKey.LINK, "<:arrow:123456789012345678>");

            assertThat(icons.get(IconKey.LINK)).isPresent();
        }
    }

    @Nested
    @DisplayName("button styles")
    class ButtonStyleRule {

        @Test
        @DisplayName("identity maps each role to its own style")
        void identityMapping() {
            for (ButtonRole role : ButtonRole.values()) {
                assertThat(ButtonStyles.identity().of(role)).hasToString(role.name());
            }
        }

        @Test
        @DisplayName("rejects LINK and UNKNOWN")
        void rejectsUnusableStyles() {
            assertThatThrownBy(
                            () ->
                                    ButtonStyles.identity()
                                            .with(ButtonRole.PRIMARY, ButtonStyle.LINK))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("LINK");
            assertThatThrownBy(
                            () ->
                                    ButtonStyles.identity()
                                            .with(ButtonRole.PRIMARY, ButtonStyle.UNKNOWN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("UNKNOWN");
        }

        @Test
        @DisplayName("with returns a new instance and leaves the original alone")
        void withIsImmutable() {
            ButtonStyles original = ButtonStyles.identity();

            ButtonStyles changed = original.with(ButtonRole.PRIMARY, ButtonStyle.SECONDARY);

            assertThat(changed).isNotSameAs(original);
            assertThat(original.of(ButtonRole.PRIMARY)).isEqualTo(ButtonStyle.PRIMARY);
            assertThat(changed.of(ButtonRole.PRIMARY)).isEqualTo(ButtonStyle.SECONDARY);
            assertThat(changed.equals(original)).isFalse();
            assertThat(changed.toString()).contains("PRIMARY");
        }
    }
}
