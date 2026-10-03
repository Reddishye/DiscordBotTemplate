package es.redactado.menu.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.ValidationResult;
import es.redactado.menu.api.Validator;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.preset.ButtonRole;
import es.redactado.menu.preset.Density;
import es.redactado.menu.preset.IconKey;
import es.redactado.menu.preset.Preset;
import es.redactado.menu.preset.Tone;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Renders one sample menu under every built-in preset and checks what came out.
 *
 * <p>Parameterised over the presets rather than testing each one separately, because the
 * failures this is looking for are of the shape "this component ignores the preset". A
 * test for one preset would pass while the component kept reading a hardcoded value, and
 * the point of a preset is that no component does.
 */
class PresetRenderingGoldenTest {

    private static final String TITLE = "Profile";
    private static final String SUBTITLE = "Ada Lovelace";
    private static final String IMAGE = "https://example.com/a.png";
    private static final String LINK = "https://example.com/docs";

    static Stream<Arguments> presets() {
        return Stream.of(
                        BuiltinPresets.DEFAULT,
                        BuiltinPresets.MINIMAL,
                        BuiltinPresets.MIDNIGHT,
                        BuiltinPresets.VIBRANT,
                        BuiltinPresets.MONOCHROME)
                .map(preset -> Arguments.of(preset.name(), preset));
    }

    /** The sample menu, one of everything the components can render. */
    private static Container render(Preset preset) {
        MenuContext ctx = context(preset);
        return MenuBuilder.create("profile")
                .add(Header.of(TITLE).subtitle(SUBTITLE).icon(IconKey.INFO))
                .add(Text.of("Body text"))
                .add(Field.of("Name", "Ada"))
                .add(Field.editable("Email", "ada@example.com", "edit").icon(IconKey.EDIT))
                .add(Divider.line())
                .add(Divider.space())
                .add(
                        Row.of(
                                ActionButton.primary("a", "A"),
                                ActionButton.secondary("b", "B"),
                                ActionButton.success("c", "C"),
                                ActionButton.danger("d", "D")))
                .add(Row.of(ActionButton.primary("e", "E").icon(IconKey.BACK)))
                .add(Row.of(LinkButton.of(LINK, "Docs").icon(IconKey.LINK)))
                .add(Row.of(SelectMenu.of("pick", "Pick one").option("a", "A").option("b", "B")))
                .add(Gallery.of(IMAGE))
                .build(ctx);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presets")
    @DisplayName("the container takes the preset's accent colour")
    void containerAccentMatchesPalette(String name, Preset preset) {
        Container container = render(preset);

        assertThat(container.getAccentColorRaw()).isEqualTo(preset.palette().accent());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presets")
    @DisplayName("the header starts with exactly level hash characters")
    void headerPrefixMatchesLevel(String name, Preset preset) {
        String content = firstText(preset);

        assertThat(content).startsWith("#".repeat(preset.header().level()) + " ");
        assertThat(content).doesNotStartWith("#".repeat(preset.header().level() + 1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presets")
    @DisplayName("the subtitle appears exactly when the preset wants one")
    void subtitleFollowsPreset(String name, Preset preset) {
        String content = firstText(preset);

        if (preset.header().subtitle()) {
            assertThat(content)
                    .as("header level %d shows a subtitle", preset.header().level())
                    .contains("\n-# " + SUBTITLE);
        } else {
            assertThat(content)
                    .as("no subtitle at header level %d", preset.header().level())
                    .doesNotContain(SUBTITLE);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presets")
    @DisplayName("the header icon appears exactly when the preset defines one")
    void headerIconFollowsPreset(String name, Preset preset) {
        String content = firstText(preset);
        String icon = preset.icons().formatted(IconKey.INFO);

        if (icon.isEmpty()) {
            assertThat(content).startsWith("#".repeat(preset.header().level()) + " " + TITLE);
        } else {
            assertThat(content).contains(icon + " " + TITLE);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presets")
    @DisplayName("every button style comes from the preset's mapping")
    void buttonStylesFollowPreset(String name, Preset preset) {
        List<Button> buttons = buttons(render(preset));

        // Row one has one button per role, row two a primary with a preset icon, row
        // three the link. The indices below name those positions.
        assertThat(buttons).hasSize(6);
        for (ButtonRole role : ButtonRole.values()) {
            assertThat(buttons.stream().filter(b -> b.getStyle() == preset.buttons().of(role)))
                    .as(
                            "at least one button in the %s style for role %s",
                            preset.buttons().of(role), role)
                    .isNotEmpty();
        }
        assertThat(buttons.get(5).getStyle())
                .as("the last button is the link")
                .isEqualTo(ButtonStyle.LINK);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presets")
    @DisplayName("a button icon appears exactly when the preset defines one")
    void buttonIconsFollowPreset(String name, Preset preset) {
        Button back = buttons(render(preset)).get(4);

        if (preset.icons().formatted(IconKey.BACK).isEmpty()) {
            assertThat(back.getEmoji()).isNull();
        } else {
            assertThat(back.getEmoji()).isNotNull();
            assertThat(back.getEmoji().getFormatted())
                    .isEqualTo(preset.icons().formatted(IconKey.BACK));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presets")
    @DisplayName("the divider follows the preset's visibility and the effective spacing")
    void dividerFollowsPreset(String name, Preset preset) {
        List<Separator> separators = separators(render(preset));

        assertThat(separators).hasSize(2);
        Separator line = separators.get(0);
        Separator space = separators.get(1);

        assertThat(line.isDivider())
                .as("line() draws only when the preset wants a rule")
                .isEqualTo(preset.divider().visible());
        assertThat(space.isDivider()).as("space() never draws").isFalse();

        Separator.Spacing expected = effectiveSpacing(preset);
        assertThat(line.getSpacing()).isEqualTo(expected);
        assertThat(space.getSpacing()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presets")
    @DisplayName("the footer appears exactly when the preset has one, with placeholders filled")
    void footerFollowsPreset(String name, Preset preset) {
        String footer = lastSmallText(preset);

        if (preset.footer().isEmpty()) {
            assertThat(footer).as("no footer for %s", name).isNull();
        } else {
            assertThat(footer).as("footer for %s", name).isNotNull();
            assertThat(footer).doesNotContain("{user}").doesNotContain("{menu}");
            assertThat(footer).contains("profile");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presets")
    @DisplayName("the footer escapes markdown in the user name")
    void footerEscapesUserName(String name, Preset preset) {
        // Always built, because midnight's own footer is "{menu}" and would put no user
        // name in the output for there to be anything to escape.
        Preset withFooter = preset.toBuilder().footer("{user} in {menu}").build();
        MenuContext ctx = context(withFooter);
        Container container = MenuBuilder.create("profile").add(Text.of("x")).build(ctx);

        String footer = smallTexts(container).getLast();

        assertThat(footer)
                .as("the display name's markdown is escaped, not interpreted")
                .contains("\\*\\*Ada\\*\\*")
                .doesNotContain("**Ada**");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presets")
    @DisplayName("every preset produces a container the Validator accepts")
    void containerPassesValidation(String name, Preset preset) {
        ValidationResult result = Validator.validate(render(preset));

        assertThat(result.isValid()).as("%s must render within Discord's limits", name).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Density.class)
    @DisplayName("density decides the spacing a preset renders with")
    void densityDecidesSpacing(Density density) {
        Preset preset = BuiltinPresets.DEFAULT.toBuilder().density(density).build();

        assertThat(effectiveSpacing(preset))
                .isEqualTo(
                        switch (density) {
                            case COMPACT -> Separator.Spacing.SMALL;
                            case NORMAL -> Separator.Spacing.SMALL;
                            case COMFORTABLE -> Separator.Spacing.LARGE;
                        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presets")
    @DisplayName("each tone selects its own palette colour")
    void toneSelectsPaletteColour(String name, Preset preset) {
        MenuContext ctx = context(preset);
        for (Tone tone : Tone.values()) {
            Container container =
                    MenuBuilder.create("profile").add(Text.of("x")).tone(tone).build(ctx);

            assertThat(container.getAccentColorRaw())
                    .as("%s in tone %s", name, tone)
                    .isEqualTo(preset.palette().color(tone));
        }
    }

    @Test
    @DisplayName("an explicit accent colour overrides the tone")
    void explicitAccentOverridesTone() {
        Preset preset = BuiltinPresets.DEFAULT;
        MenuContext ctx = context(preset);

        Container container =
                MenuBuilder.create("profile")
                        .add(Text.of("x"))
                        .tone(Tone.DANGER)
                        .accentColor(preset.palette().warning())
                        .build(ctx);

        assertThat(container.getAccentColorRaw()).isEqualTo(preset.palette().warning());
    }

    @Test
    @DisplayName("the default tone is ACCENT")
    void defaultToneIsAccent() {
        Preset preset = BuiltinPresets.MINIMAL;
        MenuContext ctx = context(preset);

        Container withDefault = MenuBuilder.create("profile").add(Text.of("x")).build(ctx);
        Container withAccent =
                MenuBuilder.create("profile").add(Text.of("x")).tone(Tone.ACCENT).build(ctx);

        assertThat(withDefault.getAccentColorRaw()).isEqualTo(withAccent.getAccentColorRaw());
        assertThat(withDefault.getAccentColorRaw()).isEqualTo(preset.palette().accent());
    }

    // ------------------------------------------------------------- helpers

    /** The documented rule, restated here so the tests and the code cannot drift. */
    private static Separator.Spacing effectiveSpacing(Preset preset) {
        return switch (preset.density()) {
            case COMPACT -> Separator.Spacing.SMALL;
            case NORMAL ->
                    preset.divider().gap() == es.redactado.menu.preset.Gap.LARGE
                            ? Separator.Spacing.LARGE
                            : Separator.Spacing.SMALL;
            case COMFORTABLE -> Separator.Spacing.LARGE;
        };
    }

    private static MenuContext context(Preset preset) {
        MenuContext ctx = mock(MenuContext.class);
        User user = mock(User.class);
        when(user.getEffectiveName()).thenReturn("**Ada**");
        when(ctx.menuId()).thenReturn("profile");
        when(ctx.preset()).thenReturn(preset);
        when(ctx.discordUser()).thenReturn(user);
        return ctx;
    }

    private static String firstText(Preset preset) {
        return texts(render(preset)).getFirst();
    }

    private static String lastSmallText(Preset preset) {
        List<String> small = smallTexts(render(preset));
        return small.isEmpty() ? null : small.getLast();
    }

    private static List<String> texts(Container container) {
        return children(container).stream()
                .filter(TextDisplay.class::isInstance)
                .map(child -> ((TextDisplay) child).getContent())
                .toList();
    }

    private static List<String> smallTexts(Container container) {
        return texts(container).stream()
                .filter(content -> content.startsWith("-# "))
                .map(content -> content.substring("-# ".length()))
                .toList();
    }

    private static List<Separator> separators(Container container) {
        return children(container).stream()
                .filter(Separator.class::isInstance)
                .map(c -> (Separator) c)
                .toList();
    }

    /**
     * Every button in the container, including the ones nested in an action row.
     *
     * <p>Descending matters: {@code getComponents()} returns only the container's direct
     * children, and a button always arrives inside an {@code ActionRow}, so a collector
     * that did not look inside would find none and every assertion about buttons would be
     * vacuously true.
     */
    private static List<Button> buttons(Container container) {
        List<Button> found = new ArrayList<>();
        for (ContainerChildComponent component : children(container)) {
            if (component instanceof Button button) {
                found.add(button);
            } else if (component instanceof ActionRow row) {
                row.getComponents().stream()
                        .filter(Button.class::isInstance)
                        .map(Button.class::cast)
                        .forEach(found::add);
            }
        }
        return found;
    }

    private static List<? extends ContainerChildComponent> children(Container container) {
        return container.getComponents();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presets")
    @DisplayName("a select offers the same options under every preset, having no style of its own")
    void selectIgnoresThePreset(String name, Preset preset) {
        Container container = render(preset);
        StringSelectMenu select = selects(container).getFirst();

        assertThat(select.getOptions())
                .as("%s must not change what a select offers", name)
                .extracting(SelectOption::getValue)
                .containsExactly("a", "b");
        assertThat(select.getPlaceholder()).isEqualTo("Pick one");
        assertThat(rowsHolding(container, StringSelectMenu.class::isInstance))
                .as("a select fills its row alone, as Discord requires")
                .allSatisfy(
                        row ->
                                assertThat(row.getComponents())
                                        .as("a select is never beside another item")
                                        .hasSize(1));
    }

    @Test
    @DisplayName("the sample menu exercises every component")
    void sampleMenuIsComplete() {
        Container container = render(BuiltinPresets.DEFAULT);

        assertThat(texts(container)).isNotEmpty();
        assertThat(separators(container)).hasSize(2);
        assertThat(buttons(container)).isNotEmpty();
        assertThat(selects(container)).hasSize(1);
    }

    /** The selects in the container, found by descending into the action rows. */
    private static List<StringSelectMenu> selects(Container container) {
        List<StringSelectMenu> found = new ArrayList<>();
        for (ContainerChildComponent component : children(container)) {
            if (component instanceof ActionRow row) {
                row.getComponents().stream()
                        .filter(StringSelectMenu.class::isInstance)
                        .map(StringSelectMenu.class::cast)
                        .forEach(found::add);
            }
        }
        return found;
    }

    /**
     * The action rows holding at least one item of the given kind.
     *
     * <p>Written as a row search rather than a select search because the property under
     * test belongs to the row: a select beside a button is not a select problem, it is a
     * row that Discord will drop an item from.
     */
    private static List<ActionRow> rowsHolding(
            Container container, java.util.function.Predicate<Object> kind) {
        List<ActionRow> found = new ArrayList<>();
        for (ContainerChildComponent component : children(container)) {
            if (component instanceof ActionRow row && row.getComponents().stream().anyMatch(kind)) {
                found.add(row);
            }
        }
        return found;
    }
}
